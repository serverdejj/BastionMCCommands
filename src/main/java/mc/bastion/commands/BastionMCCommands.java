package mc.bastion.commands;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.suggestion.Suggestions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BastionMCCommands extends JavaPlugin implements Listener, TabExecutor {
 private static final LegacyComponentSerializer COLORS=LegacyComponentSerializer.builder().character('&').hexColors().build();
 private record Settings(Map<String,Set<String>> groups, Set<String> deny, Set<String> exceptions, boolean op, boolean namespace, long ticks) {}
 private Settings settings;
 private YamlConfiguration yaml;
 private final Map<UUID,Policy> cache=new ConcurrentHashMap<>();
 private BukkitTask refresh;
 private File configFile;
 @Override public void onEnable(){
  saveDefaultConfig();configFile=new File(getDataFolder(),"config.yml");
  try{load();}catch(Exception e){getLogger().log(Level.SEVERE,"Configuración inválida; se desactiva el plugin. Revisa LuckPerms.",e);getServer().getPluginManager().disablePlugin(this);return;}
  Objects.requireNonNull(getCommand("bcmd")).setExecutor(this);getCommand("bcmd").setTabCompleter(this);
  getServer().getPluginManager().registerEvents(this,this); restartRefresh(); refreshAll(true);
 }
 @Override public void onDisable(){if(refresh!=null)refresh.cancel();cache.clear();}
 private Settings parse(YamlConfiguration y){
  ConfigurationSection section=y.getConfigurationSection("groups");if(section==null||!section.isConfigurationSection("default"))throw new IllegalArgumentException("Falta groups.default");
  Map<String,Set<String>> groups=new TreeMap<>();for(String g:section.getKeys(false)){if(!g.matches("[a-z0-9_-]+")||!section.isList(g+".commands"))throw new IllegalArgumentException("Grupo inválido: "+g);groups.put(g,Policy.rules(section.getStringList(g+".commands")));}
  return new Settings(Map.copyOf(groups),Policy.rules(y.getStringList("deny")),Policy.rules(y.getStringList("settings.namespaced-exceptions")),y.getBoolean("settings.op-bypass",true),y.getBoolean("settings.block-namespaced",true),Math.max(20,y.getLong("settings.refresh-ticks",40)));
 }
 private void load() throws Exception {YamlConfiguration candidate=new YamlConfiguration();candidate.load(configFile);Settings parsed=parse(candidate);yaml=candidate;settings=parsed;}
 private void restartRefresh(){if(refresh!=null)refresh.cancel();refresh=Bukkit.getScheduler().runTaskTimer(this,()->refreshAll(false),settings.ticks(),settings.ticks());}
 private Policy policy(Player p){Set<String> allow=new TreeSet<>();settings.groups().forEach((g,r)->{if(g.equals("default")||p.hasPermission("bastioncommands.group."+g))allow.addAll(r);});return new Policy((settings.op()&&p.isOp())||p.hasPermission("bastioncommands.bypass"),settings.namespace(),settings.exceptions(),Set.copyOf(allow),settings.deny());}
 private void refreshAll(boolean force){for(Player p:Bukkit.getOnlinePlayers()){Policy current=policy(p);Policy previous=cache.put(p.getUniqueId(),current);if(force||!current.equals(previous))p.updateCommands();}}
 @EventHandler public void join(PlayerJoinEvent e){Player p=e.getPlayer();cache.put(p.getUniqueId(),policy(p));Bukkit.getScheduler().runTask(this,()->{if(p.isOnline()){cache.put(p.getUniqueId(),policy(p));p.updateCommands();}});}
 @EventHandler public void quit(PlayerQuitEvent e){cache.remove(e.getPlayer().getUniqueId());}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void command(PlayerCommandPreprocessEvent e){Policy p=policy(e.getPlayer());if(!p.permitted(e.getMessage())){e.setCancelled(true);message(e.getPlayer(),"blocked");}}
 @EventHandler(priority=EventPriority.HIGHEST) public void roots(PlayerCommandSendEvent e){Policy p=policy(e.getPlayer());cache.put(e.getPlayer().getUniqueId(),p);e.getCommands().removeIf(s->!p.visible(s));}
 // Paper exposes the per-client command tree. Only prune literal paths; arguments remain governed by the execution guard.
 @EventHandler(priority=EventPriority.HIGHEST) public void tree(AsyncPlayerSendCommandsEvent<?> e){if(e.isAsynchronous())return;Policy p=policy(e.getPlayer());if(!p.bypass())prune(e.getCommandNode(),"",p);}
 private void prune(CommandNode<?> node,String path,Policy p){Iterator<? extends CommandNode<?>> it=node.getChildren().iterator();while(it.hasNext()){CommandNode<?> child=it.next();if(!(child instanceof LiteralCommandNode<?>))continue;String next=path.isEmpty()?child.getName():path+" "+child.getName();if(!p.visible(next))it.remove();else prune(child,next,p);}}
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void suggestions(AsyncPlayerSendSuggestionsEvent e){String buffer=e.getBuffer();if(!buffer.startsWith("/"))return;Policy p=e.isAsynchronous()?cache.get(e.getPlayer().getUniqueId()):policy(e.getPlayer());if(p==null){e.setCancelled(true);return;}if(p.bypass())return;Suggestions s=e.getSuggestions();var filtered=s.getList().stream().filter(x->{try{return p.visible(x.apply(buffer));}catch(RuntimeException ex){return false;}}).toList();e.setSuggestions(new Suggestions(s.getRange(),filtered));}
 private Component color(String text){return COLORS.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC,TextDecoration.State.FALSE);}
 private String fill(String input,String... vars){for(int i=0;i+1<vars.length;i+=2)input=input.replace("{"+vars[i]+"}",vars[i+1]);return input;}
 private String str(String path){return yaml.getString(path,"");}
 private void message(CommandSender s,String key,String... vars){s.sendMessage(color(str("messages.prefix")+fill(str("messages."+key),vars)));}
 private boolean admin(CommandSender s){return s.hasPermission("bastioncommands.admin");}
 private boolean saveRules(String group,Set<String> updated){String path="groups."+group+".commands";List<String> before=yaml.getStringList(path);Path tmp=null;try{yaml.set(path,new ArrayList<>(new TreeSet<>(updated)));Settings candidate=parse(yaml);tmp=Files.createTempFile(getDataFolder().toPath(),"config-", ".tmp");Files.writeString(tmp,yaml.saveToString());try{Files.move(tmp,configFile.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,configFile.toPath(),StandardCopyOption.REPLACE_EXISTING);}settings=candidate;refreshAll(true);return true;}catch(Exception e){yaml.set(path,before);getLogger().log(Level.SEVERE,"No se pudo guardar config.yml",e);return false;}finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(IOException ignored){}}}
 @Override public boolean onCommand(CommandSender s,Command c,String label,String[] args){
  if(!admin(s)){message(s,"no-permission");return true;}
  String action=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
  if(action.equals("reload")){try{load();restartRefresh();refreshAll(true);message(s,"reloaded");}catch(Exception e){getLogger().log(Level.WARNING,"No se recargó la configuración",e);message(s,"failed");}return true;}
  if(action.equals("menu")){if(!(s instanceof Player p)){message(s,"player-only");return true;}if(args.length==1||args.length==0)groupMenu(p,0);else if(settings.groups().containsKey(args[1]))commandMenu(p,args[1],0);else message(s,"invalid");return true;}
  if(args.length<2){message(s,"usage");message(s,"groups","groups",String.join(", ",new TreeSet<>(settings.groups().keySet())));return true;}
  String group=args[1].toLowerCase(Locale.ROOT);Set<String> current=settings.groups().get(group);if(current==null){message(s,"invalid");return true;}
  if(action.equals("list")){message(s,"list","group",group,"commands",String.join(", ",new TreeSet<>(current)));return true;}
  if((action.equals("add")||action.equals("remove"))&&args.length>=3){String rule=Policy.normalize(String.join(" ",Arrays.copyOfRange(args,2,args.length)));if(!Policy.valid(rule)){message(s,"invalid");return true;}Set<String> copy=new TreeSet<>(current);if(action.equals("add"))copy.add(rule);else copy.remove(rule);message(s,saveRules(group,copy)?"saved":"failed");return true;}
  message(s,"usage");return true;
 }
 private List<String> registered(){TreeSet<String> roots=new TreeSet<>();for(String key:Bukkit.getCommandMap().getKnownCommands().keySet()){String n=Policy.normalize(key);if(!n.contains(":")&&Policy.valid(n))roots.add(n);}settings.groups().values().forEach(r->r.forEach(path->roots.add(path.split(" ")[0])));return new ArrayList<>(roots);}
 @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(!admin(s))return List.of();List<String> choices=List.of();if(args.length==1)choices=List.of("menu","add","remove","list","reload");else if(args.length==2&&!args[0].equalsIgnoreCase("reload"))choices=new ArrayList<>(settings.groups().keySet());else if(args.length==3&&args[0].equalsIgnoreCase("add"))choices=registered();else if(args.length==3&&args[0].equalsIgnoreCase("remove"))choices=settings.groups().getOrDefault(args[1],Set.of()).stream().map(x->x.split(" ")[0]).distinct().toList();String last=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return choices.stream().filter(x->x.startsWith(last)).sorted().toList();}
 private static final class Menu implements InventoryHolder {final String group;final int page;final Map<Integer,String> actions=new HashMap<>();Inventory inv;Menu(String group,int page){this.group=group;this.page=page;}public Inventory getInventory(){return inv;}}
 private ItemStack item(String material,String name,List<String> lore){Material m=Material.matchMaterial(str("gui.materials."+material));if(m==null||!m.isItem()||m.isAir())m=Material.PAPER;ItemStack stack=new ItemStack(m);ItemMeta meta=stack.getItemMeta();meta.displayName(color(name));meta.lore(lore.stream().map(this::color).toList());stack.setItemMeta(meta);return stack;}
 private void button(Menu menu,int slot,String action,ItemStack stack){menu.actions.put(slot,action);menu.inv.setItem(slot,stack);}
 private void navigation(Menu m,int pages){if(m.page>0)button(m,45,"prev",item("navigation",str("gui.previous"),List.of()));if(m.page+1<pages)button(m,53,"next",item("navigation",str("gui.next"),List.of()));if(m.group!=null)button(m,49,"back",item("navigation",str("gui.back"),List.of()));}
 private void groupMenu(Player p,int page){List<String> groups=new ArrayList<>(new TreeSet<>(settings.groups().keySet()));int pages=Math.max(1,(groups.size()+44)/45);page=Math.max(0,Math.min(page,pages-1));Menu m=new Menu(null,page);m.inv=Bukkit.createInventory(m,54,color(str("gui.group-title")));for(int i=page*45;i<Math.min(groups.size(),(page+1)*45);i++){String g=groups.get(i);button(m,i%45,"group:"+g,item("group",fill(str("gui.group-name"),"group",g),yaml.getStringList("gui.group-lore")));}navigation(m,pages);p.openInventory(m.inv);}
 private void commandMenu(Player p,String group,int page){Set<String> rules=settings.groups().get(group);if(rules==null){groupMenu(p,0);return;}List<String> commands=registered();int pages=Math.max(1,(commands.size()+44)/45);page=Math.max(0,Math.min(page,pages-1));Menu m=new Menu(group,page);m.inv=Bukkit.createInventory(m,54,color(fill(str("gui.title"),"group",group,"page",String.valueOf(page+1),"pages",String.valueOf(pages))));for(int i=page*45;i<Math.min(commands.size(),(page+1)*45);i++){String root=commands.get(i);String state=rules.contains(root+" *")?"enabled":rules.stream().anyMatch(r->r.equals(root)||r.startsWith(root+" "))?"partial":"disabled";List<String> lore=yaml.getStringList("gui.command-lore").stream().map(x->fill(x,"state",str("gui."+state))).toList();button(m,i%45,"cmd:"+root,item(state,fill(str("gui.command-name"),"command",root),lore));}navigation(m,pages);p.openInventory(m.inv);}
 @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Menu m))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||!admin(p)||e.getRawSlot()<0||e.getRawSlot()>=54||!e.getClick().isLeftClick())return;String action=m.actions.get(e.getRawSlot());if(action==null)return;Bukkit.getScheduler().runTask(this,()->{if(!p.isOnline()||!admin(p)||p.getOpenInventory().getTopInventory().getHolder()!=m)return;if(action.equals("back")){groupMenu(p,0);return;}if(action.equals("prev")||action.equals("next")){int page=m.page+(action.equals("prev")?-1:1);if(m.group==null)groupMenu(p,page);else commandMenu(p,m.group,page);return;}if(action.startsWith("group:")){commandMenu(p,action.substring(6),0);return;}if(action.startsWith("cmd:")&&m.group!=null){Set<String> old=settings.groups().get(m.group);if(old==null){groupMenu(p,0);return;}String root=action.substring(4);Set<String> updated=new TreeSet<>(old);String wildcard=root+" *";if(!updated.remove(wildcard))updated.add(wildcard);message(p,saveRules(m.group,updated)?"saved":"failed");commandMenu(p,m.group,m.page);}});}
 @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
}
