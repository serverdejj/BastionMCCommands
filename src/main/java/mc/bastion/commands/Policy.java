package mc.bastion.commands;
import java.util.*;
record Policy(boolean bypass, boolean blockNamespace, Set<String> exceptions, Set<String> allow, Set<String> deny) {
 static String normalize(String s) { return s.strip().replaceFirst("^/", "").strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
 static boolean valid(String s) { return s.matches("[a-z0-9_:.?+-]+(?: [^ *]+)*(?: \\*)?"); }
 static boolean matches(String rule, String input) { if(rule.endsWith(" *")){String base=rule.substring(0,rule.length()-2);return input.equals(base)||input.startsWith(base+" ");}return rule.equals(input); }
 boolean forbidden(String path){String root=path.split(" ",2)[0];return (blockNamespace&&root.contains(":")&&!exceptions.contains(root))||deny.stream().anyMatch(r->matches(r,path));}
 boolean permitted(String input){String path=normalize(input);return bypass||(!path.isEmpty()&&!forbidden(path)&&allow.stream().anyMatch(r->matches(r,path)));}
 boolean visible(String input){String path=normalize(input);return bypass||(!path.isEmpty()&&!forbidden(path)&&(permitted(path)||allow.stream().anyMatch(r->r.startsWith(path+" "))));}
 static Set<String> rules(List<String> strings){Set<String> result=new TreeSet<>();for(String s:strings){String n=normalize(s);if(!valid(n))throw new IllegalArgumentException("Ruta inválida: "+s);result.add(n);}return Set.copyOf(result);}
}
