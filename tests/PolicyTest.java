package mc.bastion.commands;
import java.util.*;
public class PolicyTest {
 public static void main(String[] a){Policy p=new Policy(false,true,Set.of(),Set.of("ah","ah sell *","spawn *","clan *"),Set.of("clan admin *"));
  assert p.permitted("/AH sell 100");assert p.permitted("/ah");assert !p.permitted("/ah admin");assert !p.permitted("/ah selling");assert !p.permitted("/bukkit:plugins");assert !p.permitted("/plugins");assert !p.permitted("/clan   ADMIN reload");assert p.visible("ah sell");assert !p.visible("ah admin");assert p.permitted("/spawn");assert !p.permitted("/spawnevil");assert Policy.valid("ah sell *");assert !Policy.valid("ah * admin");System.out.println("Policy: OK");}
}
