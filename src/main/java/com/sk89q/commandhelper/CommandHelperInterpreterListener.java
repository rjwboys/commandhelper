/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sk89q.commandhelper;

import com.laytonsmith.aliasengine.Constructs.Token;
import com.laytonsmith.aliasengine.Env;
import com.laytonsmith.aliasengine.GenericTreeNode;
import com.laytonsmith.aliasengine.MScriptCompiler;
import com.laytonsmith.aliasengine.MScriptComplete;
import com.laytonsmith.aliasengine.Static;
import com.laytonsmith.aliasengine.exceptions.CancelCommandException;
import com.laytonsmith.aliasengine.exceptions.ConfigCompileException;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 *
 * @author Layton
 */
public class CommandHelperInterpreterListener extends PlayerListener {

    Set<String> interpreterMode = new HashSet<String>();
    Map<String, String> multilineMode = new HashMap<String, String>();

    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        if (interpreterMode.contains(event.getPlayer().getName())) {
            Player p = event.getPlayer();
            textLine(p, event.getMessage());
            event.setCancelled(true);
        }

    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        interpreterMode.remove(event.getPlayer().getName());
        multilineMode.remove(event.getPlayer().getName());
    }

    @Override
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (interpreterMode.contains(event.getPlayer().getName())) {
            Player p = event.getPlayer();
            textLine(p, event.getMessage());
            event.setCancelled(true);
        }
    }

    public void textLine(Player p, String line) {
        if (line.equals("-")) {
            //Exit interpreter mode
            interpreterMode.remove(p.getName());
            Static.SendMessage(p, ChatColor.YELLOW + "Now exiting interpreter mode");
        } else if (line.equals(">>>")) {
            //Start multiline mode
            if (multilineMode.containsKey(p.getName())) {
                Static.SendMessage(p, ChatColor.RED + "You are already in multiline mode!");
            } else {
                multilineMode.put(p.getName(), "");
                Static.SendMessage(p, ChatColor.YELLOW + "You are now in multiline mode. Type <<< on a line by itself to execute.");
                Static.SendMessage(p, ":" + ChatColor.GRAY + ">>>");
            }
        } else if (line.equals("<<<")) {
            //Execute multiline
            Static.SendMessage(p, ":" + ChatColor.GRAY + "<<<");
            String script = multilineMode.get(p.getName());
            multilineMode.remove(p.getName());
            try {
                execute(script, p);
            } catch (ConfigCompileException e) {
                Static.SendMessage(p, ChatColor.RED + e.getMessage() + ":" + e.getLineNum());
            }
        } else {
            if (multilineMode.containsKey(p.getName())) {
                //Queue multiline
                multilineMode.put(p.getName(), multilineMode.get(p.getName()) + line + "\n");
                Static.SendMessage(p, ":" + ChatColor.GRAY + line);
            } else {
                try {
                    //Execute single line
                    execute(line, p);
                } catch (ConfigCompileException ex) {
                    Static.SendMessage(p, ChatColor.RED + ex.getMessage());
                }
            }
        }
    }

    public void reload() {
    }

    public void execute(String script, final Player p) throws ConfigCompileException {
        List<Token> stream = MScriptCompiler.lex("include('plugins/CommandHelper/auto_include.ms')\n" + script, new File("Interpreter"));
        GenericTreeNode tree = MScriptCompiler.compile(stream);
        interpreterMode.remove(p.getName());
        Env env = new Env();
        env.SetPlayer(p);
        try {
            MScriptCompiler.execute(tree, env, new MScriptComplete() {

                public void done(String output) {
                    output = output.trim();
                    if (output.equals("")) {
                        Static.SendMessage(p, ":");
                    } else {
                        if (output.startsWith("/")) {
                            //Run the command
                            Static.SendMessage(p, ":" + ChatColor.YELLOW + output);
                            p.chat(output);
                        } else {
                            //output the results
                            Static.SendMessage(p, ":" + ChatColor.GREEN + output);
                        }
                    }
                    interpreterMode.add(p.getName());
                }
            }, null);
        } catch (CancelCommandException e) {
            interpreterMode.add(p.getName());
        } catch(Exception e){
            Static.SendMessage(p, ChatColor.RED + e.toString());
            e.printStackTrace();
            interpreterMode.add(p.getName());
        }
    }

    public void startInterpret(String playername) {
        interpreterMode.add(playername);
    }
}
