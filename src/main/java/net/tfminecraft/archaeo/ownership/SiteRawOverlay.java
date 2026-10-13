package net.tfminecraft.archaeo.ownership;

import java.io.IOException;
import java.util.*;

/** Recognized fields change narrowly; unknown maps/list-row fields and nulls survive. */
public final class SiteRawOverlay {
    private SiteRawOverlay(){}
    private static final Set<String> ROOT=Set.of("schema","actors-version","actor-refs","id","serial","type","status","interest","name","world","chunk-x","chunk-z","surface-y","created-by","created-at","director","visibility","recovered-count","hint-ids","excavators","workers","factions","establishment","prospect","strata","finds","jornada","fill-damage");
    private static final Set<String> FIND=Set.of("id","artifact-id","item","given-name","stratum","conservation","buried-conservation","state","cells","prior-cells","grazed-cells","direct-hit-cells","cleaned-cells","brush-remaining","find-number","recovered-by","recovered-at","studied","lab-cleaned","field-sketch","study-notes","interpretations");
    private static Set<String> fields(String path,Map<String,Object> before,Map<String,Object> after){
        if(path.isEmpty())return ROOT;
        if(path.equals("establishment"))return Set.of("chunk-x","chunk-z","camp-x","camp-y","camp-z","sign-x","sign-y","sign-z","blocks","wool-primary","wool-secondary","facing");
        if(path.equals("prospect"))return Set.of("samples","confirmed");
        if(path.equals("jornada"))return Set.of("world-day","pick-left");
        if(path.matches("workers\\.[^.]+"))return Set.of("role","blocks-removed","cells-brushed","finds-recovered","finds-damaged","finds-lost","joined-at","last-active-at");
        if(path.matches("strata\\.[^.]+"))return Set.of("present","disturbed","min-y","max-y");
        if(path.matches("actor-refs\\.[^.]+"))return Set.of("account","character");
        if(path.equals("finds[]"))return FIND;
        if(path.equals("finds[].interpretations[]"))return Set.of("type","id","author","at");
        if(Set.of("workers","strata","actor-refs","prospect.samples").contains(path)){Set<String> keys=new LinkedHashSet<>(before.keySet());keys.addAll(after.keySet());return keys;}
        return after.keySet();
    }
    public static Map<String,Object> patch(Map<String,Object> before,Map<String,Object> known){return merge(before==null?Map.of():before,known,"");}
    private static Map<String,Object> merge(Map<String,Object> before,Map<String,Object> after,String path){
        Map<String,Object> result=SiteDocuments.copy(before);
        for(String key:fields(path,before,after)){
            if(!after.containsKey(key)){
                if(before.containsKey(key))result.put(key,before.get(key) instanceof Map<?,?>?new LinkedHashMap<>():before.get(key) instanceof List<?>?new ArrayList<>():null);
                continue;
            }
            String child=path.isEmpty()?key:path+"."+key;Object old=before.get(key),value=after.get(key);
            if(value instanceof Map<?,?>){result.put(key,merge(old instanceof Map<?,?>?SiteDocuments.map(old):Map.of(),SiteDocuments.map(value),child));}
            else if(value instanceof List<?> list&&(child.equals("finds")||child.equals("finds[].interpretations"))){
                List<?> prior=old instanceof List<?> original?original:List.of();List<Object> rows=new ArrayList<>();
                for(int i=0;i<list.size();i++){
                    Map<String,Object> row=SiteDocuments.map(list.get(i));Map<String,Object> original=Map.of();
                    if(child.equals("finds")){for(Object candidate:prior)if(Objects.equals(row.get("id"),SiteDocuments.map(candidate).get("id")))original=SiteDocuments.map(candidate);}
                    else if(i<prior.size())original=SiteDocuments.map(prior.get(i));
                    rows.add(merge(original,row,child+"[]"));
                }
                result.put(key,rows);
            }else result.put(key,value);
        }
        return result;
    }
    public static void validateReadable(Map<String,Object> raw,Map<String,Object> known)throws IOException{check(raw,known,"");}
    private static void check(Map<String,Object> raw,Map<String,Object> known,String path)throws IOException{
        for(String key:fields(path,raw,known)){
            if(!raw.containsKey(key)||raw.get(key)==null)continue;
            Object value=raw.get(key),typed=known.get(key);String child=path.isEmpty()?key:path+"."+key;
            if(value instanceof Map<?,?>&&typed instanceof Map<?,?>)check(SiteDocuments.map(value),SiteDocuments.map(typed),child);
            else if(value instanceof List<?> list&&(child.equals("finds")||child.equals("finds[].interpretations"))&&typed instanceof List<?> rows){
                if(list.size()!=rows.size())throw new IOException("Unloaded dossier rows "+child);
                for(int i=0;i<list.size();i++)check(SiteDocuments.map(list.get(i)),SiteDocuments.map(rows.get(i)),child+"[]");
            }else if(!Objects.equals(value,typed))throw new IOException("Unreadable recognized dossier field "+child);
        }
    }
}
