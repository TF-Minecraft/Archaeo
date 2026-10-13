import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import uuid

import yaml

spec = importlib.util.spec_from_file_location("site_ownership", Path(__file__).with_name("site-ownership.py"))
sites = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sites)


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "Archaeo"
        self.root.mkdir()
        self.backup = Path(self.temp.name) / "recovery"
        self.account, self.character, self.worker_account, self.worker = [str(uuid.uuid4()) for _ in range(4)]
        self.key = str(uuid.uuid4())
        self.name = self.key + ".yml"
        self.original = {
            "schema": 1, "id": self.key, "world": "world", "director": self.account,
            "createdBy": self.account, "excavators": [self.worker_account],
            "workers": {self.account: {"role": "director", "finds-recovered": 7, "future": None},
                        self.worker_account: {"role": "excavator", "cells-brushed": 8}},
            "prospect": {"samples": {self.worker_account: [{"x": 3, "y": 6, "z": 8, "future": None}]},
                         "confirmed": [self.worker_account], "future": {"nested": None}},
            "finds": [{"id": str(uuid.uuid4()), "future": [None, {"raw": "kept"}]}],
            "future": {"nullable": None, "numbers": [True, 1, 1.0], "binary": b"\x00\x01\xff"},
        }
        self.mapping = {self.account: {"account": self.account, "character": self.character},
                        self.worker_account: {"account": self.worker_account, "character": self.worker}}
        self.before = "# exact original bytes\r\n" + yaml.safe_dump(self.original, sort_keys=False).replace("\n", "\r\n")
        self.output = sites.migrated(self.original, self.mapping)
        self.after = yaml.safe_dump(self.output, sort_keys=False)
        self.target = self.root / "sites" / self.name
        self.source = self.root / "ownership/source" / self.name
        self.plan = self.root / "ownership/plans" / self.name
        self.journal = self.root / "ownership/journals" / ("0000000000001-" + str(uuid.uuid4()) + ".yml")
        self.saved = {"version": 1, "id": self.journal.stem, "kind": "migration", "file": self.name,
                      "before": self.before, "after": self.after, "done": True, "legacy-map": self.mapping}
        self.write(self.target, self.after.encode())
        self.write(self.source, self.before.encode())
        self.write_yaml(self.plan, {"version": 1, "source": self.before, "legacy-map": self.mapping})
        self.write_yaml(self.journal, self.saved)

    @staticmethod
    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def write_yaml(self, path, value):
        self.write(path, yaml.safe_dump(value, sort_keys=False).encode())

    def apply(self):
        return sites.inspect(self.root, True, self.backup)

    def assert_unchanged(self):
        self.assertEqual(self.target.read_bytes(), self.after.encode())
        self.assertTrue(self.journal.exists())

    def test_completed_migration_restores_exact_crlf_bytes_after_verified_full_backup(self):
        config = self.root / "config.yml"
        opaque = self.root / "cache" / "opaque.bin"
        self.write(config, b"unknown: null\n")
        self.write(opaque, b"\x00\xffretained")
        before = sites.hashes(self.root)
        result = self.apply()
        self.assertEqual(result["journals"][0]["result"], "restored original bytes")
        self.assertEqual(self.before.encode(), self.target.read_bytes())
        self.assertEqual(before, sites.hashes(self.backup / "data"))
        self.assertTrue((self.backup / "SHA256.json").exists())
        self.assertEqual(b"\x00\xffretained", opaque.read_bytes())
        self.assertFalse(self.journal.exists())
        self.assertTrue((self.root / "ownership/rolled-back/journals" / self.journal.name).exists())

    def test_gameplay_drift_and_new_dossier_are_preserved(self):
        changed = copy.deepcopy(self.output)
        changed["workers"][self.worker]["cells-brushed"] += 1
        self.write_yaml(self.target, changed)
        current = self.target.read_bytes()
        new = copy.deepcopy(self.output)
        new["id"] = str(uuid.uuid4())
        new_path = self.root / "sites" / (new["id"] + ".yml")
        self.write_yaml(new_path, new)
        result = self.apply()
        self.assertIn("changed", result["journals"][0]["result"])
        self.assertEqual(current, self.target.read_bytes())
        self.assertIn(str(new_path), result["unplanned_dossiers"])
        self.assertTrue(self.journal.exists())
        self.assertEqual(current, (self.backup / "data/sites" / self.name).read_bytes())

    def add_estate(self):
        account, character, death = [str(uuid.uuid4()) for _ in range(3)]
        recipient = {"account": account, "character": character}
        before = copy.deepcopy(self.output)
        after = copy.deepcopy(before)
        after["actor-refs"][character] = recipient
        after["director"] = character
        estate = {"death": death, "revision": 1, "from": self.mapping[self.account], "to": recipient,
                  "decision": f"EstateDecision[deathId={death}, testator=CharacterRef[ownerId={self.account}, characterId={self.character}], diedAt=1970-01-01T00:00:00Z, property=Character[ref=CharacterRef[ownerId={account}, characterId={character}]], guildLocks=Frozen[], revision=1]"}
        path = self.journal.with_name("0000000000002-" + str(uuid.uuid4()) + ".yml")
        self.write_yaml(path, {"version": 1, "id": path.stem, "kind": "estate", "file": self.name,
                               "before": self.after, "after": yaml.safe_dump(after), "done": True, "estate": estate})
        return path, after

    def test_estate_history_prevents_automatic_migration_rollback_even_after_source_restore(self):
        self.add_estate()
        result = self.apply()
        self.assertIn("estate history", result["journals"][0]["result"])
        self.assert_unchanged()

    def test_invalid_later_journal_blocks_all_restore_without_touching_earlier_valid_data(self):
        later = self.journal.with_name("zz-invalid.yml")
        self.write(later, b"version: 1\nkind: migration\n")
        before = sites.hashes(self.root)
        result = self.apply()
        self.assertIn("apply_error", result)
        self.assertEqual(before, sites.hashes(self.root))
        self.assertFalse(self.backup.exists())

    def test_semantic_drift_with_valid_raw_snapshots_and_type_coercions_is_rejected(self):
        for field, value in [("future", {"nullable": "lost"}), ("createdBy", str(uuid.uuid4())),
                             ("workers", {}), ("director", self.worker)]:
            output = copy.deepcopy(self.output)
            output[field] = value
            saved = dict(self.saved, after=yaml.safe_dump(output))
            self.write_yaml(self.journal, saved)
            result = sites.inspect(self.root)
            self.assertIn("error", result["journals"][0], field)
            self.assert_unchanged()
        output = copy.deepcopy(self.output)
        output["future"]["numbers"] = [1, True, 1]
        self.write_yaml(self.journal, dict(self.saved, after=yaml.safe_dump(output)))
        self.assertIn("error", sites.inspect(self.root)["journals"][0])

    def test_fixed_mapping_archive_fullrefs_and_unsupported_versions_fail_closed(self):
        for key, value in [("version", 1.5), ("version", True), ("version", "1"), ("file", "../outside.yml")]:
            self.write_yaml(self.journal, dict(self.saved, **{key: value}))
            self.assertIn("error", sites.inspect(self.root)["journals"][0])
            self.assert_unchanged()
        self.write_yaml(self.journal, self.saved)
        plan = {"version": 1, "source": self.before, "legacy-map": copy.deepcopy(self.mapping)}
        plan["legacy-map"][self.account]["account"] = str(uuid.uuid4())
        self.write_yaml(self.plan, plan)
        self.assertIn("error", sites.inspect(self.root)["journals"][0])
        self.write_yaml(self.plan, {"version": 1, "source": self.before, "legacy-map": self.mapping})
        extra = copy.deepcopy(self.mapping)
        extra[str(uuid.uuid4())] = None
        self.write_yaml(self.plan, {"version": 1, "source": self.before, "legacy-map": extra})
        self.write_yaml(self.journal, dict(self.saved, **{"legacy-map": extra}))
        self.assertIn("error", sites.inspect(self.root)["journals"][0])
        self.write_yaml(self.plan, {"version": 1, "source": self.before, "legacy-map": self.mapping})
        self.write_yaml(self.journal, self.saved)
        self.write(self.source, b"changed archive")
        self.assertIn("error", sites.inspect(self.root)["journals"][0])

    def test_duplicate_nested_keys_recursive_documents_and_bad_inventory_block_all_effects(self):
        self.assertRaises(ValueError, sites.document, "a: {nullable: null, nullable: 1}")
        self.assertRaises(ValueError, sites.document, "a: &loop [*loop]")
        duplicate = self.root / "sites" / "wrong-name.yml"
        self.write(duplicate, self.after.encode())
        before = sites.hashes(self.root)
        result = self.apply()
        self.assertIn("apply_error", result)
        self.assertTrue(result["held_dossiers"])
        self.assertEqual(before, sites.hashes(self.root))

    def test_missing_and_incomplete_rows_are_preserved(self):
        self.target.unlink()
        result = self.apply()
        self.assertIn("missing", result["journals"][0]["result"])
        self.assertFalse(self.target.exists())
        self.assertTrue(self.journal.exists())
        self.write(self.target, self.after.encode())
        self.write_yaml(self.journal, dict(self.saved, done=False))
        self.assertIn("incomplete", sites.inspect(self.root)["journals"][0]["result"])

    def test_backup_failure_existing_destination_and_symlinks_never_mutate_dossiers(self):
        with patch.object(sites.shutil, "copytree", side_effect=OSError("backup disk full")):
            self.assertRaises(OSError, self.apply)
        self.assert_unchanged()
        self.assertRaises(FileExistsError, self.apply)
        self.assertRaises(ValueError, sites.inspect, self.root, True)
        self.assertRaises(ValueError, sites.backup_all, self.root, self.root / "unsafe-backup")
        (self.root / "outside-link").symlink_to(Path(self.temp.name))
        self.assertRaises(ValueError, sites.inspect, self.root)
        self.assert_unchanged()

    def test_estate_receipt_full_decision_revision_and_ownership_only_changes_are_validated(self):
        path, after = self.add_estate()
        self.assertEqual("estate", sites.receipt(self.root, path)["kind"])
        valid = sites.document(sites.text(path))
        for key, value in [("revision", 1.5), ("death", str(uuid.uuid4())), ("decision", "wrong")]:
            saved = copy.deepcopy(valid)
            saved["estate"][key] = value
            self.write_yaml(path, saved)
            self.assertRaises(ValueError, sites.receipt, self.root, path)
        changed = copy.deepcopy(after)
        changed["workers"][self.worker]["cells-brushed"] += 1
        self.write_yaml(path, dict(valid, after=yaml.safe_dump(changed)))
        self.assertRaises(ValueError, sites.receipt, self.root, path)

    def add_placement(self, done=False):
        path = self.root / "ownership/placements" / (str(uuid.uuid4()) + ".yml")
        actor = self.mapping[self.account]
        saved = {"version": 1, "kind": "camp-placement", "id": path.stem, "file": self.name,
                 "actor": actor, "session": f"CharacterSession[ref=CharacterRef[ownerId={self.account}, characterId={self.character}], sessionId=1]",
                 "moving": True, "before": self.after, "after": self.after, "world": "world",
                 "blocks": [{"x": 1, "y": 2, "z": 3, "before": "minecraft:air", "after": "minecraft:stone"}],
                 "kit": "opaque full kit payload", "done": done}
        self.write_yaml(path, saved)
        return path, saved

    def test_completed_and_pending_placement_history_is_retained_even_when_dossier_matches_migration(self):
        path, saved = self.add_placement()
        for done in (False, True):
            self.write_yaml(path, dict(saved, done=done))
            result = sites.inspect(self.root)
            self.assertIn("placement history", result["journals"][0]["result"])
            self.assertEqual(done, result["placements"][0]["done"])
            self.assert_unchanged()
        result = self.apply()
        self.assertIn("placement history", result["journals"][0]["result"])
        self.assert_unchanged()
        self.assertTrue(path.exists())

    def test_malformed_placement_inventory_blocks_every_restore(self):
        path, valid = self.add_placement()
        for field, value in [("file", "../outside.yml"), ("version", 1.5), ("session", "wrong"),
                             ("before", None), ("world", "other-world"), ("done", "true"),
                             ("blocks", [{"x": 1.0, "y": 2, "z": 3, "before": "air", "after": "stone"}])]:
            self.write_yaml(path, dict(valid, **{field: value}))
            before = sites.hashes(self.root)
            result = self.apply()
            self.assertIn("apply_error", result, field)
            self.assertIn("error", result["placements"][0], field)
            self.assertEqual(before, sites.hashes(self.root))
            self.assertFalse(self.backup.exists())
        self.write_yaml(path, dict(valid, before=None, moving=False))
        self.assertEqual(self.name, sites.placement(path)["file"])

    def add_completion(self, journals=None):
        if journals is None:
            journals = []
        death = str(uuid.uuid4())
        moved = []
        for saved in journals:
            death = saved["estate"]["death"]
            moved.append(saved["file"][:-4])
        source = self.mapping[self.account]
        saved = {"version": 1, "kind": "estate-completion", "death": death, "revision": 1,
                 "from": source, "to": None, "property": "Frozen[]", "counts": {"sites": len(moved)}, "moved": moved,
                 "retained": {self.name: source},
                 "decision": f"EstateDecision[deathId={death}, testator=CharacterRef[ownerId={self.account}, characterId={self.character}], diedAt=1970-01-01T00:00:00Z, property=Frozen[], guildLocks=Frozen[], revision=1]"}
        if journals:
            saved["to"] = journals[0]["estate"]["to"]
            target = saved["to"]
            saved["property"] = f"Character[ref=CharacterRef[ownerId={target['account']}, characterId={target['character']}]]"
            saved["decision"] = journals[0]["estate"]["decision"]
        path = self.root / "ownership/completions" / death / "1.yml"
        self.write_yaml(path, saved)
        return path, saved

    def test_completion_and_superseded_history_remain_manual_recovery(self):
        stamp, _ = self.add_completion()
        result = self.apply()
        self.assertIn("estate history", result["journals"][0]["result"])
        self.assert_unchanged()
        self.assertTrue(stamp.exists())
        estate, _ = self.add_estate()
        saved = sites.document(sites.text(estate))
        saved["done"] = False
        archived = self.root / "ownership/superseded" / estate.name
        self.write_yaml(archived, saved)
        estate.unlink()
        result = sites.inspect(self.root)
        self.assertNotIn("error", result["superseded"][0])
        self.assertIn("estate history", result["journals"][0]["result"])
        stamp.unlink()
        completed, record = self.add_completion([saved])
        record["moved"] = []
        record["counts"] = {"sites": 0}
        self.write_yaml(completed, record)
        self.assertNotIn("error", sites.inspect(self.root)["completions"][0])
        conflicting = dict(record, to=None, property="Frozen[]", decision=record["decision"].replace(record["property"], "Frozen[]"))
        self.write_yaml(completed, conflicting)
        result = self.apply()
        self.assertIn("error", result["completions"][0])
        self.assertIn("apply_error", result)
        self.assert_unchanged()

    def test_completion_count_decision_paths_and_receipt_ids_are_bound_before_any_apply(self):
        path, valid = self.add_completion()
        for key, value in [("counts", {"sites": 1}), ("moved", [self.key]), ("revision", 1.5),
                           ("death", str(uuid.uuid4())), ("decision", "wrong"), ("retained", {"../outside.yml": None}),
                           ("retained", {"bad.yml": None})]:
            self.write_yaml(path, dict(valid, **{key: value}))
            before = sites.hashes(self.root)
            result = self.apply()
            self.assertIn("apply_error", result, key)
            self.assertEqual(before, sites.hashes(self.root))
            self.assertFalse(self.backup.exists())
        path.unlink()
        estate, _ = self.add_estate()
        journal = sites.document(sites.text(estate))
        stamp, completed = self.add_completion([journal])
        self.assertNotIn("error", sites.inspect(self.root)["completions"][0])
        conflicting = dict(completed, to=None, property="Frozen[]", decision=completed["decision"].replace(completed["property"], "Frozen[]"))
        self.write_yaml(stamp, conflicting)
        self.assertIn("error", sites.inspect(self.root)["completions"][0])
        self.write_yaml(stamp, completed)
        estate.unlink()
        result = self.apply()
        self.assertIn("apply_error", result)
        self.assertIn("error", result["completions"][0])
        self.assert_unchanged()


if __name__ == "__main__":
    unittest.main()
