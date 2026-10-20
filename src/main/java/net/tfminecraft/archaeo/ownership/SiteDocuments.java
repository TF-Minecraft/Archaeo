package net.tfminecraft.archaeo.ownership;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;

/** Raw maps retain explicit nulls and fields the runtime does not understand. */
public final class SiteDocuments {
    private SiteDocuments() {}
    @SuppressWarnings("unchecked")
    public static Map<String,Object> parse(String text) throws IOException {
        try {
            LoaderOptions options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setAllowRecursiveKeys(false);
            // Bukkit saves shared empty lists as aliases and reads them without SnakeYAML's 50-alias cap.
            options.setMaxAliasesForCollections(Integer.MAX_VALUE);
            Object value=new Yaml(new SafeConstructor(options)).load(text);
            if(!(value instanceof Map<?,?> map))throw new IOException("Dossier must be an object");
            checkKeys(map,Collections.newSetFromMap(new IdentityHashMap<>()));return (Map<String,Object>)map;
        }catch(RuntimeException failure){throw new IOException("Invalid dossier YAML",failure);}
    }
    private static void checkKeys(Object value,Set<Object> ancestors) throws IOException {
        if(value instanceof Map<?,?>||value instanceof List<?>) {
            if(!ancestors.add(value)||ancestors.size()>100)throw new IOException("Recursive or excessively nested dossier");
            if(value instanceof Map<?,?> map)for(var entry:map.entrySet()){if(!(entry.getKey() instanceof String))throw new IOException("Nontext dossier key");checkKeys(entry.getValue(),ancestors);}
            else for(Object item:(List<?>)value)checkKeys(item,ancestors);
            ancestors.remove(value);
        }
    }
    public static String text(Map<String,Object> map){return new Yaml().dump(map);}
    public static boolean same(Object left,Object right){return new Yaml().dump(ordered(left)).equals(new Yaml().dump(ordered(right)));}
    private static Object ordered(Object value){
        if(value instanceof Map<?,?> map){Map<String,Object> result=new TreeMap<>();map.forEach((key,item)->result.put((String)key,ordered(item)));return result;}
        if(value instanceof List<?> list)return list.stream().map(SiteDocuments::ordered).toList();
        return value;
    }
    public static Map<String,Object> copy(Map<String,Object> map){try{return parse(text(map));}catch(IOException failure){throw new IllegalArgumentException(failure);}}
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    public static String read(Path path)throws IOException{return Files.exists(path)?Files.readString(path):null;}
    public static void forceDirectory(Path path)throws IOException{
        // The Windows provider cannot open directory FileChannels. File contents are still
        // forced before the atomic replacement; directory fsync remains enabled on Unix.
        if(path.getFileSystem().getSeparator().equals("\\"))return;
        try(FileChannel directory=FileChannel.open(path,READ)){directory.force(true);}
    }
    public static void write(Path path,String text)throws IOException {
        Files.createDirectories(path.getParent());
        for(Path parent=path.toAbsolutePath().getParent();parent!=null;parent=parent.getParent())forceDirectory(parent);
        Path temporary=path.resolveSibling(path.getFileName()+".next");
        try(FileChannel file=FileChannel.open(temporary,CREATE,TRUNCATE_EXISTING,WRITE)){ByteBuffer buffer=StandardCharsets.UTF_8.encode(text);while(buffer.hasRemaining())file.write(buffer);file.force(true);}
        Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        forceDirectory(path.getParent());
    }
}
