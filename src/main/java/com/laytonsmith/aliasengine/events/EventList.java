/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.laytonsmith.aliasengine.events;

import com.laytonsmith.aliasengine.events.drivers.CWeatherListener;
import com.laytonsmith.aliasengine.events.drivers.CServerListener;
import com.laytonsmith.aliasengine.events.drivers.CEntityListener;
import com.laytonsmith.aliasengine.events.drivers.CWorldListener;
import com.laytonsmith.aliasengine.events.drivers.CPlayerListener;
import com.laytonsmith.aliasengine.events.drivers.CVehicleListener;
import com.laytonsmith.aliasengine.events.drivers.CBlockListener;
import com.laytonsmith.aliasengine.events.drivers.CInventoryListener;
import com.laytonsmith.PureUtilities.ClassDiscovery;
import com.laytonsmith.aliasengine.Constructs.Construct;
import com.laytonsmith.aliasengine.Static;
import com.laytonsmith.aliasengine.api;
import com.sk89q.commandhelper.CommandHelperPlugin;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.Event.Category;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Listener;

/**
 *
 * @author layton
 */
public class EventList {
    private static final Map<org.bukkit.event.Event.Type, SortedSet<Event>> event_list =
            new EnumMap<org.bukkit.event.Event.Type, SortedSet<Event>>(org.bukkit.event.Event.Type.class);
    static {
        //Initialize all our events as soon as we start up
        initEvents();
    }
    
    /**
     * A more efficient lookup, this method will return a value in near constant time,
     * as opposed to the other getEvent, which will return in O(n) time. This could
     * return null if there is no event named name.
     */
    public static Event getEvent(org.bukkit.event.Event.Type type, String name){
        if(type == null){
            return getEvent(name);
        }
        SortedSet<Event> set = event_list.get(type);
        if(set != null){
            Iterator<Event> i = set.iterator();
            while(i.hasNext()){
                Event e = i.next();
                if(e.getName().equals(name)){
                    return e;
                }
            }
        }
        return null;
    }
    /**
     * This could return null if there is no event named name.
     * @param name
     * @return 
     */
    public static Event getEvent(String name){
        for(org.bukkit.event.Event.Type type : event_list.keySet()){
            SortedSet<Event> set = event_list.get(type);
            Iterator<Event> i = set.iterator();
            while(i.hasNext()){
                Event e = i.next();
                if(e.getName().equals(name)){
                    return e;
                }
            }
        }
        return null;
    }
    
    private static void initEvents() {
        //Register internal classes first, so they can't be overridden
        Class[] classes = ClassDiscovery.DiscoverClasses(EventList.class, null, null);
        int total = 0;
        for (int i = 0; i < classes.length; i++) {
            Annotation[] a = classes[i].getAnnotations();
            for (int j = 0; j < a.length; j++) {
                Annotation ann = a[j];
                if (ann.annotationType().equals(api.class)) {
                    Class api = classes[i];
                    String apiClass = (api.getEnclosingClass() != null
                            ? api.getEnclosingClass().getName().split("\\.")[api.getEnclosingClass().getName().split("\\.").length - 1]
                            : "<global>");
                    if (Event.class.isAssignableFrom(api)) {
                        try {
                            Event e = (Event) api.newInstance();
                            registerEvent(e, apiClass);
                            total++;
                        } catch (InstantiationException ex) {
                            Logger.getLogger(EventList.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IllegalAccessException ex) {
                            Logger.getLogger(EventList.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {
                        System.out.println("@api events must implement " + EventList.class.getPackage().getName() + ".Event! " + api.getSimpleName() + " cannot be loaded.");
                    }
                }
            }
        }
        
        if((Boolean)com.laytonsmith.aliasengine.Static.getPreferences().getPreference("debug-mode")){
            System.out.println("CommandHelper: Loaded " + total + " event" + (total==1?"":"s"));
        }
    }
    
    public static void registerEvent(Event e, String apiClass) {
        if(!apiClass.equals("Sandbox")){
            if((Boolean)com.laytonsmith.aliasengine.Static.getPreferences().getPreference("debug-mode")){
                System.out.println("CommandHelper: Loaded event \"" + e.getName() + "\"");
            }
        }
        if(!event_list.containsKey(e.driver())){
            event_list.put(e.driver(), new TreeSet<Event>());
        }
        event_list.get(e.driver()).add(e);
        try{
            e.hook();
        } catch(UnsupportedOperationException ex){}
    }
    

    public static final CBlockListener BlockListener = new CBlockListener();
    public static final CEntityListener EntityListener = new CEntityListener();
    public static final CInventoryListener InventoryListener = new CInventoryListener();
    public static final CPlayerListener PlayerListener = new CPlayerListener();
    public static final CServerListener ServerListener = new CServerListener();
    public static final CVehicleListener VehicleListener = new CVehicleListener();
    public static final CWeatherListener WeatherListener = new CWeatherListener();
    public static final CWorldListener WorldListener = new CWorldListener();
    
    /**
     * This should be called when the plugin starts up. It registeres all bukkit event listeners.
     * This should only be called once, in onEnable from the main plugin.
     */
    public static void Startup(CommandHelperPlugin chp){
        for(org.bukkit.event.Event.Type type : event_list.keySet()){
            SortedSet<Event> set = event_list.get(type);
            Iterator<Event> i = set.iterator();
            while(i.hasNext()){
                Event e = i.next();
                Listener l = null;
                Category c = e.driver().getCategory();
                switch(c){
                    case BLOCK:
                        l = BlockListener;
                        break;
                    case ENTITY:
                        l = EntityListener;
                        break;
                    case INVENTORY:
                        l = InventoryListener;
                        break;
                    case PLAYER:
                        l = PlayerListener;
                        break;
                    case SERVER:
                        l = ServerListener;
                        break;
                    case VEHICLE:
                        l = VehicleListener;
                        break;
                    case WEATHER:
                        l = WeatherListener;
                        break;
                    case WORLD:
                        l = WorldListener;
                        break;
                }
                chp.registerEvent(e.driver(), l, Priority.Lowest);
            }
        }
    }
}
