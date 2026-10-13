#!/usr/bin/env python3
"""Inspect dossiers and conservatively undo untouched completed actor migrations offline."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import uuid

import yaml


class DossierLoader(yaml.SafeLoader):
    def construct_mapping(self, node, deep=False):
        self.flatten_mapping(node)
        result = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            if not isinstance(key, str) or key in result:
                raise ValueError("duplicate or nontext YAML key")
            result[key] = self.construct_object(value_node, deep=deep)
        return result


def document(text):
    result = yaml.load(text, Loader=DossierLoader)
    if not isinstance(result, dict):
        raise ValueError("dossier must be a mapping")
    def check(value, ancestors):
        if isinstance(value, (dict, list)):
            if id(value) in ancestors or len(ancestors) >= 100:
                raise ValueError("recursive or excessively nested dossier")
            nested = ancestors | {id(value)}
            for item in value.values() if isinstance(value, dict) else value:
                check(item, nested)
    check(result, set())
    return result


def text(path):
    return path.read_bytes().decode("utf-8")


def identity(value):
    if not isinstance(value, str) or str(uuid.UUID(value)) != value:
        raise ValueError("invalid UUID identity")
    return value


def integral(value, expected=None):
    if type(value) is not int or value < 0 or value > 9223372036854775807:
        raise ValueError("unsupported integral version/revision")
    if expected is not None and value != expected:
        raise ValueError("unsupported version")
    return value


def same(left, right):
    if type(left) is not type(right):
        return False
    if isinstance(left, dict):
        return left.keys() == right.keys() and all(same(left[key], right[key]) for key in left)
    if isinstance(left, list):
        return len(left) == len(right) and all(same(a, b) for a, b in zip(left, right))
    return left == right


def ref(value):
    if not isinstance(value, dict):
        raise ValueError("invalid full actor reference")
    return identity(value["account"]), identity(value["character"])


def actors(raw):
    result = set()
    if raw.get("director") is not None:
        result.add(identity(raw["director"]))
    excavators = raw.get("excavators", [])
    if not isinstance(excavators, list) or len(set(excavators)) != len(excavators):
        raise ValueError("invalid/duplicate excavator slots")
    result.update(identity(value) for value in excavators)
    workers = raw.get("workers", {})
    if not isinstance(workers, dict):
        raise ValueError("invalid worker slots")
    for actor, worker in workers.items():
        result.add(identity(actor))
        if not isinstance(worker, dict) or worker.get("role") not in (None, "director", "archaeologist", "excavator"):
            raise ValueError("invalid worker record")
    prospect = raw.get("prospect", {})
    if not isinstance(prospect, dict) or not isinstance(prospect.get("samples", {}), dict) or not isinstance(prospect.get("confirmed", []), list):
        raise ValueError("invalid prospect slots")
    result.update(identity(value) for value in prospect.get("samples", {}))
    result.update(identity(value) for value in prospect.get("confirmed", []))
    return result


def validate(raw):
    identity(raw["id"])
    integral(raw.get("schema", 1), 2)
    integral(raw.get("actors-version"), 1)
    refs = raw.get("actor-refs")
    if not isinstance(refs, dict):
        raise ValueError("missing actor refs")
    for character, value in refs.items():
        if identity(character) != ref(value)[1]:
            raise ValueError("actor ref/key conflict")
    if not actors(raw).issubset(refs):
        raise ValueError("unresolved dossier actor")


def migrated(before, mapping):
    integral(before.get("schema", 1), 1)
    if "actor-refs" in before or "actors-version" in before or not isinstance(mapping, dict):
        raise ValueError("legacy reserved actor fields or fixed mapping conflict")
    if set(mapping) != actors(before):
        raise ValueError("fixed mapping actor inventory mismatch")
    output = copy.deepcopy(before)
    refs = {}
    for account in actors(before):
        mapped_account, character = ref(mapping[account])
        if mapped_account != account or (character in refs and not same(refs[character], mapping[account])):
            raise ValueError("reviewed actor collision/account mismatch")
        refs[character] = copy.deepcopy(mapping[account])
    output.update({"schema": 2, "actors-version": 1, "actor-refs": refs})
    def mapped(account):
        return ref(mapping[account])[1]
    if before.get("director") is not None:
        output["director"] = mapped(before["director"])
    if "excavators" in before:
        output["excavators"] = [mapped(value) for value in before["excavators"]]
    if "workers" in before:
        output["workers"] = {mapped(key): copy.deepcopy(value) for key, value in before["workers"].items()}
    if "prospect" in before:
        prospect = output["prospect"]
        if "samples" in prospect:
            prospect["samples"] = {mapped(key): value for key, value in prospect["samples"].items()}
        if "confirmed" in prospect:
            prospect["confirmed"] = [mapped(value) for value in prospect["confirmed"]]
    validate(output)
    return output


def receipt(root, path):
    saved = document(text(path))
    integral(saved.get("version"), 1)
    if saved.get("id") != path.stem or type(saved.get("done")) is not bool or saved.get("kind") not in ("migration", "estate"):
        raise ValueError("journal ID/path/state/kind mismatch")
    before_text, after_text = saved["before"], saved["after"]
    if not isinstance(before_text, str) or not isinstance(after_text, str):
        raise ValueError("missing immutable dossier snapshots")
    before, after = document(before_text), document(after_text)
    name = identity(before["id"]) + ".yml"
    if saved.get("file") != name or after.get("id") != before["id"]:
        raise ValueError("journal dossier identity/path mismatch")
    validate(after)
    if saved["kind"] == "migration":
        if "estate" in saved or text(root / "ownership/source" / name) != before_text:
            raise ValueError("migration archive/envelope mismatch")
        plan = document(text(root / "ownership/plans" / name))
        integral(plan.get("version"), 1)
        if plan.get("source") != before_text or not same(plan.get("legacy-map"), saved.get("legacy-map")):
            raise ValueError("fixed legacy plan mismatch")
        if not same(migrated(before, saved["legacy-map"]), after):
            raise ValueError("migration changes unrelated dossier contents")
    else:
        validate(before)
        estate = saved["estate"]
        death = identity(estate["death"])
        revision = integral(estate["revision"])
        source, target = ref(estate["from"]), ref(estate["to"])
        decision = estate["decision"]
        def reference(value):
            return f"CharacterRef[ownerId={value[0]}, characterId={value[1]}]"
        if source == target or source != ref(before["actor-refs"][before["director"]]):
            raise ValueError("estate source director mismatch")
        if not isinstance(decision, str) or not decision.startswith(f"EstateDecision[deathId={death}, testator={reference(source)}, diedAt=") or f", property=Character[ref={reference(target)}], guildLocks=" not in decision or not decision.endswith(f", revision={revision}]"):
            raise ValueError("captured full estate decision mismatch")
        expected = copy.deepcopy(before)
        existing = expected["actor-refs"].get(target[1])
        if existing is not None and ref(existing) != target:
            raise ValueError("estate recipient account collision")
        expected["actor-refs"].setdefault(target[1], copy.deepcopy(estate["to"]))
        expected["director"] = target[1]
        if not same(expected, after):
            raise ValueError("estate changes unrelated dossier contents")
    return saved


def placement(path):
    saved = document(text(path))
    integral(saved.get("version"), 1)
    if saved.get("kind") != "camp-placement" or identity(saved["id"]) != path.stem or type(saved.get("done")) is not bool or type(saved.get("moving")) is not bool:
        raise ValueError("invalid placement header")
    after = document(saved["after"])
    validate(after)
    name = identity(after["id"]) + ".yml"
    actor = ref(saved["actor"])
    if saved.get("file") != name or actor != ref(after["actor-refs"][after["director"]]):
        raise ValueError("placement dossier/actor binding mismatch")
    session = saved["session"]
    prefix = f"CharacterSession[ref=CharacterRef[ownerId={actor[0]}, characterId={actor[1]}], sessionId="
    suffix = re.fullmatch(r"-?(0|[1-9][0-9]*)]", session[len(prefix):]) if isinstance(session, str) and session.startswith(prefix) else None
    if suffix is None or not -9223372036854775808 <= int(session[len(prefix):-1]) <= 9223372036854775807:
        raise ValueError("placement captured session mismatch")
    if saved.get("before") is not None:
        before = document(saved["before"])
        validate(before)
        if before["id"] != after["id"] or saved["moving"] and ref(before["actor-refs"][before["director"]]) != actor:
            raise ValueError("placement source mismatch")
    elif saved["moving"]:
        raise ValueError("moving placement has no original dossier")
    world, blocks, kit = saved.get("world"), saved.get("blocks"), saved.get("kit")
    if not isinstance(world, str) or world != after.get("world") or not isinstance(blocks, list) or not blocks or kit is not None and not isinstance(kit, str):
        raise ValueError("invalid placement effects evidence")
    coordinates = set()
    for block in blocks:
        if not isinstance(block, dict) or any(type(block.get(key)) is not int or not -2147483648 <= block[key] <= 2147483647 for key in ("x", "y", "z")) or not isinstance(block.get("before"), str) or not isinstance(block.get("after"), str):
            raise ValueError("invalid placement block snapshots")
        key = tuple(block[field] for field in ("x", "y", "z"))
        if key in coordinates:
            raise ValueError("duplicate placement block snapshots")
        coordinates.add(key)
    return saved


def completion(path, receipts):
    saved = document(text(path))
    integral(saved.get("version"), 1)
    death = identity(saved["death"])
    revision = integral(saved["revision"])
    source = ref(saved["from"])
    if saved.get("kind") != "estate-completion" or path.stem != str(revision) or path.parent.name != death:
        raise ValueError("invalid completion path/header")
    target, property_text = saved.get("to"), saved["property"]
    reference = lambda value: f"CharacterRef[ownerId={value[0]}, characterId={value[1]}]"
    if target is not None:
        if property_text != "Character[ref=" + reference(ref(target)) + "]":
            raise ValueError("completion full recipient mismatch")
    elif property_text not in ("Frozen[]", "Waiting[]", "Sender[]"):
        raise ValueError("unsupported completion recipient")
    captured = saved["decision"]
    if not isinstance(captured, str) or not captured.startswith(f"EstateDecision[deathId={death}, testator={reference(source)}, diedAt=") or f", property={property_text}, guildLocks=" not in captured or not captured.endswith(f", revision={revision}]"):
        raise ValueError("completion full decision mismatch")
    moved = saved["moved"]
    if not isinstance(moved, list) or any(identity(key) != key for key in moved) or len(set(moved)) != len(moved):
        raise ValueError("invalid completion moved IDs")
    counts = saved["counts"]
    if not isinstance(counts, dict) or set(counts) != {"sites"} or integral(counts["sites"]) != len(moved):
        raise ValueError("completion moved count mismatch")
    expected = set()
    for journal in receipts:
        estate = journal.get("estate")
        if estate is not None and estate["death"] == death and integral(estate["revision"]) <= revision:
            if ref(estate["from"]) != source:
                raise ValueError("completion/receipt source mismatch")
            if integral(estate["revision"]) == revision and estate["decision"] != captured:
                raise ValueError("completion/receipt same revision decision mismatch")
            if journal.get("done"):
                expected.add(journal["file"][:-4])
    if set(moved) != expected:
        raise ValueError("completion IDs differ from cumulative completed receipts")
    retained = saved["retained"]
    if not isinstance(retained, dict):
        raise ValueError("invalid retained completion inventory")
    for name, actor in retained.items():
        if not isinstance(name, str) or Path(name).name != name or "\\" in name or not name.endswith(".yml") or actor is not None and ref(actor) != source:
            raise ValueError("invalid retained dossier/full ref")
        identity(name[:-4])
    return saved


def force(folder):
    for parent in (folder, *folder.parents):
        descriptor = os.open(parent, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)


def durable(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    force(path.parent)
    descriptor, temporary = tempfile.mkstemp(prefix="site-ownership-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        force(path.parent)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def hashes(root):
    result = {}
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError("symlink in plugin data requires manual recovery")
        if path.is_file():
            result[str(path.relative_to(root))] = hashlib.sha256(path.read_bytes()).hexdigest()
    return result


def backup_all(root, backup):
    backup = Path(backup).resolve()
    if backup == root or root in backup.parents:
        raise ValueError("backup must be outside plugin data")
    before = hashes(root)
    backup.mkdir(parents=True)
    shutil.copytree(root, backup / "data")
    for path in (backup / "data").rglob("*"):
        if path.is_file():
            with path.open("rb") as stream:
                os.fsync(stream.fileno())
        elif path.is_dir():
            force(path)
    if hashes(backup / "data") != before or hashes(root) != before:
        raise ValueError("current-state recovery backup verification failed")
    durable(backup / "SHA256.json", json.dumps(before, indent=2).encode())
    force(backup / "data")


def inspect(root, apply=False, backup=None):
    root = Path(root).resolve()
    if not root.is_dir():
        raise ValueError("plugin data directory is missing")
    if any(path.is_symlink() for path in root.rglob("*")):
        raise ValueError("symlink in plugin data requires manual recovery")
    report = {"journals": [], "superseded": [], "completions": [], "placements": [], "held_dossiers": [], "unplanned_dossiers": []}
    actions, estate_files, placement_files, planned = [], set(), set(), set()
    receipts, decisions, completed_ids = [], {}, set()
    def bind(saved):
        if saved["kind"] != "estate":
            return
        estate = saved["estate"]
        key = estate["death"], integral(estate["revision"])
        if key in decisions and decisions[key] != estate["decision"]:
            raise ValueError("same revision estate receipt decisions disagree")
        decisions[key] = estate["decision"]
        if saved["done"]:
            key = estate["death"], saved["file"]
            if key in completed_ids:
                raise ValueError("duplicate completed estate property ID")
            completed_ids.add(key)
    failed = False
    for path in sorted((root / "ownership/placements").glob("*.yml")):
        row = {"journal": str(path)}
        try:
            saved = placement(path)
            placement_files.add(saved["file"])
            row.update({"file": saved["file"], "done": saved["done"], "result": "placement evidence retained; manual recovery"})
        except (OSError, ValueError, KeyError, TypeError, AttributeError, yaml.YAMLError) as error:
            row["error"] = str(error)
            failed = True
        report["placements"].append(row)
    journals = sorted((root / "ownership/journals").glob("*.yml"))
    for path in journals:
        row = {"journal": str(path)}
        try:
            saved = receipt(root, path)
            bind(saved)
            receipts.append(saved)
            row.update({"file": saved["file"], "kind": saved["kind"]})
            if saved["kind"] == "estate":
                estate_files.add(saved["file"])
                row["result"] = "estate receipt retained; manual recovery"
            elif not saved["done"]:
                row["result"] = "incomplete migration retained; manual recovery"
            else:
                actions.append((path, saved, row))
                planned.add(saved["file"])
        except (OSError, ValueError, KeyError, TypeError, AttributeError, yaml.YAMLError) as error:
            row["error"] = str(error)
            failed = True
        report["journals"].append(row)
    for path in sorted((root / "ownership/superseded").glob("*.yml")):
        row = {"journal": str(path)}
        try:
            saved = receipt(root, path)
            if saved["kind"] != "estate" or saved["done"]:
                raise ValueError("superseded evidence is not an untouched estate intent")
            bind(saved)
            receipts.append(saved)
            estate_files.add(saved["file"])
            row.update({"file": saved["file"], "result": "superseded estate evidence retained; manual recovery"})
        except (OSError, ValueError, KeyError, TypeError, AttributeError, yaml.YAMLError) as error:
            row["error"] = str(error)
            failed = True
        report["superseded"].append(row)
    for path in sorted((root / "ownership/completions").glob("*/*.yml")):
        row = {"journal": str(path)}
        try:
            saved = completion(path, receipts)
            estate_files.update(key + ".yml" for key in saved["moved"])
            estate_files.update(saved["retained"])
            row.update({"death": saved["death"], "revision": saved["revision"], "result": "completion evidence retained; manual recovery"})
        except (OSError, ValueError, KeyError, TypeError, AttributeError, yaml.YAMLError) as error:
            row["error"] = str(error)
            failed = True
        report["completions"].append(row)
    candidates = []
    for path, saved, row in actions:
        target = root / "sites" / saved["file"]
        archive = root / "ownership/rolled-back/journals" / path.name
        current = target.read_bytes() if target.is_file() else None
        if saved["file"] in placement_files:
            row["result"] = "placement history retained; manual recovery"
        elif saved["file"] in estate_files:
            row["result"] = "estate history retained; manual recovery"
        elif archive.exists():
            row["result"] = "existing rollback archive; manual recovery"
        elif current not in (saved["before"].encode(), saved["after"].encode()):
            row["result"] = "changed or missing dossier; preserve current data"
        else:
            row["result"] = "safe completed migration rollback"
            candidates.append((path, saved, row, current))
    seen = {}
    for path in sorted((root / "sites").glob("*.yml")):
        if path.name not in planned:
            report["unplanned_dossiers"].append(str(path))
        try:
            raw = document(text(path))
            identity(raw["id"])
            if path.name != raw["id"] + ".yml" or raw["id"] in seen:
                raise ValueError("duplicate dossier ID or filename mismatch")
            seen[raw["id"]] = path
        except (OSError, ValueError, KeyError, TypeError, AttributeError, yaml.YAMLError) as error:
            report["held_dossiers"].append({"file": str(path), "error": str(error)})
            failed = True
    if apply:
        if backup is None:
            raise ValueError("--apply requires --backup with a new directory")
        if failed:
            report["apply_error"] = "invalid inventory; no dossier changed"
            return report
        backup_all(root, backup)
        for path, saved, row, expected in candidates:
            target = root / "sites" / saved["file"]
            if target.read_bytes() != expected:
                raise ValueError("dossier changed after recovery preflight")
            durable(target, saved["before"].encode())
            archive = root / "ownership/rolled-back/journals" / path.name
            archive.parent.mkdir(parents=True, exist_ok=True)
            force(archive.parent)
            os.link(path, archive)
            force(archive.parent)
            path.unlink()
            force(path.parent)
            row["result"] = "restored original bytes"
            row["archived"] = str(archive)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True, help="plugins/Archaeo; Paper must be stopped")
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--backup", type=Path, help="new recovery directory outside plugin data")
    args = parser.parse_args()
    print(json.dumps(inspect(args.data, args.apply, args.backup), indent=2))


if __name__ == "__main__":
    main()
