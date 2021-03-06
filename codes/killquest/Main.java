package lockless.killquest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;


public class Main extends JavaPlugin implements Listener
{


	private double checkTotalQuestRadius=15000;
	private double onePartAffectArea=1000;
	//floders
	private File pluginFolderPath;
	private String foldername="killquest";
	private String saveFileKillquestAreasCleared="killquestWorld";
	private String saveFileVictory="VictoryList";
	private String saveFileAreaNames="areanames";

	
	
	private int assumedSize;
	private boolean[] killquestClearBool;//i have the brain capacity to code this, not that
	private int[][] killquestQuests;//i have the brain capacity to code this, not that
	private ArrayList <Player> killquestQuesters;//i have the brain capacity to code this, not that
	private ArrayList <QuesterAndScore> ScrollOfQuestingKnights;
	
	//debug true for more info
	private boolean logMobDeathsToConsole=false;
	
	//chaos is raid or some quaziloreshit	
	private int chaosOfNature=0;

	
	
	private boolean canSpawnRAID = true;
	private int[] raiderSpawnTypesData;
	private int raiderSpawnTypesIndex=0;
	
	private enum raidPowerCalculator{chaos , scoreAvg, scoreMax }
	private raidPowerCalculator howPowerMath=raidPowerCalculator.chaos;
	
	private int[] spawnMobAmounts;
	private ArrayList<Location> raidSpawnLocations;//where raiders spawn, in the midst of players if desperate for alocation.
	private ArrayList<Creature> raiders;//these get AI turned of later
	private ArrayList<Creature> raidersThatRide;//these get AI turned on on spawn
	private raidCoordinate lastRaidSpot;//for help turning the canraid back true
	
	private ArrayList<Player> raidInitialTargets;//living players

	private ArrayList<raiderIsTargetingYouCount> debugTargetingRng;//used in multiple methods, fuck passing lsitsthem around it var now
	private World mainWorld;//getWorld(0)
	private BukkitRunnable raidSpawningTask; //spawns shit calls raider or ridingraider
	private BukkitRunnable raidTargetingTask; // turns on ai for some

	private int simplierRaiderCount=0; //used in runnable
	private int simplierRaiderIndex=0; //used in runnable
	
	
	
	private boolean namingsomeAreasChanged=false;//when is win
	private boolean namingsmbdCouldNameIt=false;
	private Player namingTheOneWhoNames;
	private int namingAreaIndex=-1;
	private String[] areaNames;

	
	
	private class QuesterAndScore
	{
		public Player player;
		public int score;
		public int currentIndex;
		public String name ="";
		
		public QuesterAndScore(Player p ,int areaIndex) 
		{
			score = 0;
			player = p;
			name = p.getName();
			currentIndex = areaIndex;
		}
		
		public void AddScore(int index , int addScore) 
		{
			if(currentIndex == index) 
			{
				score += addScore;
			}
			else 
			{
				currentIndex= index;
				score = addScore;
			}
		}
		public boolean isThis(String someName) 
		{
			return (name==someName);
		}
		
		
	}
	
	private class raidCoordinate
	{
		double x;
		double z;


		public raidCoordinate(double setX,double setZ)
		{
			x = setX;
			z = setZ;
		}
		public String getString() 
		{
			return (""+(int)x+" "+(int)z+"");
		}
	}
	
	
	private class raiderIsTargetingYouCount
	{
		Player you;
		int count=0;
		public  raiderIsTargetingYouCount(Player p) 
		{
			you = p;
			count = 0;
		}
		public void plusplus() 
		{
			count++;
		}
		public int getCount() 
		{
			return count;
		}
		public String name() 
		{
			return you.getName();
		}
	}
	
	
	

	public void onEnable() 
	{
		int sideSize = 2*(int)(checkTotalQuestRadius/onePartAffectArea);
		assumedSize = sideSize*sideSize;
		System.out.println("assumed size of killquest arrays " + assumedSize);
		killquestClearBool = new boolean[assumedSize];
		killquestQuests = new int[assumedSize][4];
		killquestQuesters = new  ArrayList<Player>();
		ScrollOfQuestingKnights = new ArrayList<QuesterAndScore>();
		areaNames = new String[assumedSize];
		folderCreation();
		doSaveAreaBooleans();
		
		DoLoadAreaNames();//is null texts
		
		getServer().getPluginManager().registerEvents(this, this);
		System.out.println("killquest enabled");
	}
	
	public void onDisable() 
	{
		doSaveAreaBooleans();
		if(namingsomeAreasChanged==true) 
		{
			doSaveAreaNamesAll();
			
		}
		System.out.println("killquest saving data and disabling");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) 
	{
		
		if(label.equalsIgnoreCase("killquest")) 
		{
			if(sender instanceof Player) 
			{
				if (args.length == 0) {
					PlayerCommandLogic((Player) sender);
				}
				else 
				{
					PlayerNamesAreaCommandLogic((Player) sender,args);
				}
				return true;
			}
			else 
			{
				ConsoleCommandLogic(sender);
			}
			
			
			
			
			return true;
		}
		
		else if(label.equalsIgnoreCase("killquestraidpls")) 
		{
			if(sender instanceof Player) 
			{
				doRaid((Player) sender);
				return true;
			}
		}
		return false;
	}
	
	private void PlayerNamesAreaCommandLogic(Player p,String[] args) 
	{
		System.out.println("would execute naming area");
		if (namingsmbdCouldNameIt==true) 
		{
			if(namingTheOneWhoNames == p) 
			{
				if(namingAreaIndex == getLocationsKillQuestIndex(p.getLocation().getX(), p.getLocation().getZ()))
				{
					String name="";
					for(int i =0 ; i<args.length ;i++) 
					{
						name += args[i]+" ";
					}
					doSaveAreaNamesAppend(namingAreaIndex,name);
					namingsomeAreasChanged=true;
					namingAreaIndex=-1;
					System.out.println("naming area:"+name);
					giveGodApple(p);
					p.sendMessage(ChatColor.GOLD + "you've given this place a name");
				}
				else 
				{
					p.sendMessage(ChatColor.GOLD+"you'd need to return to that area, where you won ");
					return;
				}
			}
			else 
			{
				p.sendMessage("what?");
				return;
			}
		}
		else 
		{
			p.sendMessage("what?");
			return;
		}
		
	}
	
	private void PlayerCommandLogic(Player playerOne) 
	{
		int theloca = getLocationsKillQuestIndex(playerOne.getLocation().getX(), playerOne.getLocation().getZ());
		if(playerIsWithinTrackingRange(playerOne)) 
		{
			//PlayerCommandLogic(playerOne);//derpiest bug ive coded in 2021
			playerOne.sendMessage(ChatColor.GOLD+"you are in "+ChatColor.UNDERLINE+areaNames[theloca]);
			
		}
		else 
		{
			playerOne.sendMessage(ChatColor.LIGHT_PURPLE+"killquest only works about "+((int)(checkTotalQuestRadius/1000))+"k blocks off zero zero ");
			return;
		}
		
		if(canSpawnRAID==false) 
		{
			canSpawnRAID = raidStatusFromraiderLists();
			if(canSpawnRAID) 
			{
				playerOne.sendMessage(ChatColor.GOLD+"There air feels fresher");		
				System.out.println(" ");
				System.out.println("RAIDING RESET");
				System.out.println(" ");
			}
		}
		
		//System.out.println("executing normal player killquest");
		
		
		
		//maps
		//killquester
		
		if(killquestClearBool[theloca]) 
		{
			playerOne.sendMessage(ChatColor.GOLD+"There seems to be no monsters around here, in "+areaNames[theloca]);
			return;
		} 
		
		if (killquestQuesters.contains(playerOne)==false)
		{
			playerOne.sendMessage(ChatColor.GOLD + "Started tracking your kills for "+areaNames[theloca]);
			killquestQuesters.add(playerOne);
			ScrollOfQuestingKnights.add(new QuesterAndScore(playerOne, theloca));
			
		}
		else 
		{
			
			//look for the score of the dude in the good area theloca
			boolean foundperfectmatch=false;
			for(QuesterAndScore qs:ScrollOfQuestingKnights) 
			{
				if(qs.isThis(playerOne.getName())) 
				{
					if(qs.currentIndex == theloca) 
					{
						System.out.println("[killquest] perfect match in "+theloca);
						foundperfectmatch= true;
						playerOne.sendMessage(ChatColor.GOLD+"your score:"+qs.score);
						break;
						
					}
					
				}
			}
			if(foundperfectmatch==false) 
			{
				playerOne.sendMessage(ChatColor.GOLD+""+ChatColor.UNDERLINE+"your score: 0");
				ScrollOfQuestingKnights.add(new QuesterAndScore(playerOne,0));
			}
			
		}
		
		if(killquestClearBool[theloca]==false) 
		{
			playerOne.sendMessage(Progress(theloca));
		}
		
	}

	
	private void ConsoleCommandLogic(CommandSender cmd) 
	{
		if (howPowerMath == raidPowerCalculator.chaos) 
		{
			System.out.println("[killquest] max score math");
			howPowerMath = raidPowerCalculator.scoreMax;
		}else if(howPowerMath == raidPowerCalculator.scoreMax) 
		{
			System.out.println("[killquest] average score math");
			howPowerMath = raidPowerCalculator.scoreAvg;
		}else if(howPowerMath == raidPowerCalculator.scoreAvg) 
		{
			System.out.println("[killquest] chaos tiers math");
			howPowerMath = raidPowerCalculator.chaos;
		}
		
		int countMaps_areaClear=0;
		for(int i = 0;i<killquestClearBool.length;i++) 
		{
			if(killquestClearBool[i]) 
				 countMaps_areaClear++;
		}
		int countMaps_killquests=0;
		for(int i =0 ;i<killquestQuests.length ; i++) 
		{
			if(killquestQuests[i].length==4) 
			{
				int yes=0;
				for(int u =0;u<4;u++) 
				{
					yes+=killquestQuests[i][u];
				}
				if(yes>8) 
				{
					countMaps_killquests++;
				}
			}
		}
		System.out.println("\n");
		System.out.println("\n");
		System.out.println(" the killquest");
		System.out.println("current chaos  = "+chaosOfNature);
		System.out.println("total :"+countMaps_areaClear+"\t Ongoing :"+countMaps_killquests);
		
		/*
		ArrayList<QuesterAndScore> remQs = new ArrayList<QuesterAndScore>();
		for(QuesterAndScore qk: ScrollOfQuestingKnights) //?
		{
			
			System.out.println(qk.name + " is in " +qk.currentIndex+ "   \t score"+qk.score);
			//dont remove offline pls
			if(qk.player.isOnline()==false) 
			{
				System.out.println("trying to remove an afk player");
				remQs.add(qk);
				//ScrollOfQuestingKnights.remove(qk);
			}
		}
		//not confusing awt all
		
		for(QuesterAndScore remqs:remQs) 
		{
			try 
			{
				ScrollOfQuestingKnights.remove(remqs);
			}
			catch(NullPointerException e)
			{
				//lets do absolutely nothing about it
			}
		}
		*/
		System.out.println("\n");
		System.out.println("\n");
		for(int i =0; i<killquestClearBool.length;i++) 
		{
			if(killquestClearBool[i]) 
			{
				System.out.println(i +" "+areaNames[i]);
			}
		}
		
		
		
	}
	
	
    //TODO see how much health the snowman actually has vs the raiders with no sethealth done on them
	LivingEntity testentity;//testvar
	Player testplayer;//testvar
	@SuppressWarnings({ "unused", "deprecation" })
    private void CommandSpawns(CommandSender sender, Command command, String label, String[] args) 
    {
    	if(sender instanceof Player) 
        {
            final Player testplayer = (Player) sender;
            
            Location loc = testplayer.getLocation();
            World w = Bukkit.getServer().getWorld("world");
            double y = loc.getY();
            double x = loc.getX();
            double z = loc.getZ();
            Location sentry = new Location(w,x,y,z);
            final Creature testentity = (Creature) Bukkit.getWorld("world").spawnEntity(sentry, EntityType.SNOWMAN);
            testentity.setMaxHealth(250.0);
            testentity.setHealth(250.0);
            testentity.setTicksLived(20 * 20);
            testentity.setRemoveWhenFarAway(false);
            testentity.setCanPickupItems(true);
            testentity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100000, 10));
            testentity.getEquipment().setHelmet(new ItemStack(Material.AIR, 1));
            testentity.getEquipment().setHelmetDropChance(0.0F);
            testentity.getEntityId();
               
       
            Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() 
            {
                public void run() 
                {
                    Player anyplayer = (Player) testplayer;
                    testentity.getTarget();
                    testentity.setTarget(anyplayer);
                    testentity.launchProjectile(Snowball.class);
                }
            },  60);
        }
    	else 
    	{
    		if(args==null)
    		{
    			return;
			}
    		else
    		{
    			String playerName = args[0];
    			boolean found=false;
    			for(Player p: getServer().getOnlinePlayers()) 
    			{
    				if(p.getName() ==playerName) 
    				{
    					found =true;
    					final Player testplayer = p;
    					break;
    				}
    			}
    			if(found==false) 
    			{
    				return;
    			}
    			
                
                Location loc = testplayer.getLocation();
                World w = Bukkit.getServer().getWorld("world");
                double y = loc.getY();
                double x = loc.getX();
                double z = loc.getZ();
                Location sentry = new Location(w,x,y,z);
                final Creature testentity = (Creature) Bukkit.getWorld("world").spawnEntity(sentry, EntityType.SNOWMAN);
                testentity.setMaxHealth(250.0);
                testentity.setHealth(250.0);
                testentity.setTicksLived(20 * 20);
                testentity.setRemoveWhenFarAway(false);
                testentity.setCanPickupItems(true);
                testentity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100000, 10));
                testentity.getEquipment().setHelmet(new ItemStack(Material.AIR, 1));
                testentity.getEquipment().setHelmetDropChance(0.0F);
                testentity.getEntityId();
                   
           
                Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() 
                {
                	public int derp=0;
                    public void run() 
                    {
                        Player anyplayer = (Player) testplayer;
                        testentity.getTarget();
                        testentity.setTarget(anyplayer);
                        testentity.launchProjectile(Snowball.class);
                    }},  60);
    		}
    	}
    }
	
	

	//logic
	
	
	
	private void RemoveEveryQuesterFromWonArea(int index) 
	{
		ArrayList<QuesterAndScore> rmList = new ArrayList<QuesterAndScore>();
		for(QuesterAndScore qs:ScrollOfQuestingKnights) 
		{
			if (qs.currentIndex == index) 
			{
				System.out.println("trying to remove after win");
				rmList.add(qs);
				//ScrollOfQuestingKnights.remove(qs);
			}
		}
		//shouldnt throw bullshit now
		for(QuesterAndScore qs:rmList) 
		{
			try 
			{
			ScrollOfQuestingKnights.remove(qs);
			}catch (Exception e) {
				// ignore exception lol
				System.out.println("fucked up removign a questing knight even with smarter logic");
			}
		}
		
	}
	
	private int getPowerLevelForCurrentRaid() 
	{
		// SCORE MAX, SCORE AVG METHODS AND CHOAS
		if (howPowerMath == raidPowerCalculator.chaos) 
		{
			System.out.println("[killquest] using chaos math");
			if (chaosOfNature < 50)return 0;

			if (chaosOfNature < 200)return 1;

			if (chaosOfNature < 400)return 2;

			if (chaosOfNature < 800)return 3;

			if (chaosOfNature < 1200)return 4;

			if (chaosOfNature < 1600)return 5;

			if (chaosOfNature < 3000)return 6;

			if (chaosOfNature < 6000)return 7;

			if (chaosOfNature < 9000)return 8;
			return 9;
		}
		else if(howPowerMath == raidPowerCalculator.scoreMax) 
		{

			System.out.println("[killquest] using max math");
			int max=0;
			for (Player p :raidInitialTargets) 
			{
				if(p.getTotalExperience()>max) 
				{
					max = p.getTotalExperience();
				}
			}
			int tier = max/1000;
			if(tier > 9) 
			{
				tier =9;
			}
			System.out.println(" bigest score is "+max+" tier set to "+tier);
			return tier;
		}
		else if(howPowerMath == raidPowerCalculator.scoreAvg) 
		{

			System.out.println("[killquest] using average math");
			int sum=0;
			for (Player p :raidInitialTargets) 
			{
				
					sum= p.getTotalExperience();
				
			}
			double avg = sum/raidInitialTargets.size();
			int tier = (int)avg/1000;
			if(tier > 9) 
			{
				tier =7;
			}
			System.out.println(" bigest score is "+avg+" tier set to "+tier);
			return tier;
		}
		
		
		
		return 0;
	}
	
	
	private boolean raidStatusFromraiderLists() 
	{
		for(Creature c : raiders) 
		{
			if (c.isDead()) 
			{
				raiders.remove(c);
			}
		}
		for(Creature c : raidersThatRide) 
		{
			if (c.isDead()) 
			{
				raidersThatRide.remove(c);
			}
		}
		
		System.out.println("doublechecked on raiders");
		return (raidersThatRide.size()==0 && raiders.size()==0) ;
	}
	
	//works. happy, nice. lore is there tooo
	private void giveGodApple(Player playerOne) 
	{
		PlayerInventory inventory = playerOne.getInventory();
		ItemStack apple= new ItemStack(Material.ENCHANTED_GOLDEN_APPLE,1);
		ItemMeta meta = apple.getItemMeta();
		List<String> lore=new ArrayList<String>();
		lore.add("For your valor");
		//lore.add(" valor"); // it'd get printed on a new line in game
		meta.setLore(lore);
		meta.setDisplayName(ChatColor.GOLD + "Apple of Victory");
		apple.setItemMeta(meta);
        inventory.addItem(apple);
	}
	
	private void spawnChaos( ArrayList<Player> targets) 
	{
		
		for (Player p : targets) 
		{
			if(p.isOnline() == false) 
			{
				System.out.println("[killquest] offline players arent in the raid targets... "+p.getName());
				targets.remove(p);
			}
		}
		
		
		if(canSpawnRAID==false) 
		{
			System.out.println("[killquest] cant spawn raid, there might be "+raiders.size()+" around "+lastRaidSpot.getString() );
			return;
		}
		if(chaosOfNature <0) 
		{

			System.out.println("[killquest] wont spawn raid, because sunshine and butterflies");
			return;
		}
		
		raidCoordinate  middle = new raidCoordinate(0,0);
		for(Player p : targets) 
		{
			middle.x += p.getLocation().getX();
			middle.z += p.getLocation().getZ();
		}
		middle.z = middle.z/targets.size();
		middle.x = middle.x/targets.size();
		//null fix
		raidSpawnLocations = new ArrayList<Location>();
		
		double checkDistance=1000;
		double gameDistance=60;
		
		for (int i =-1 ; i<=1;i++) 
		{
			
			for (int u =-1 ; u<=1;u++) 
			{
				int indexofLoca=getLocationsKillQuestIndex(middle.x+checkDistance*i, middle.z+checkDistance*u);
				System.out.println("[killquest] trying to spawn raid in loca "+indexofLoca);
				boolean thereIsSuchArea;
				if(indexofLoca >=0 && indexofLoca<900) 
				{
					thereIsSuchArea=!killquestClearBool[indexofLoca];//and its not clear
				}
				else 
				{
					System.out.println("[killquest] index is out of range ok fix");//TODO actually if out of range, then untamable - add to list of awailable spawns
					thereIsSuchArea = false;
				}
				
				if(thereIsSuchArea) 
				{
					//System.out.println("trying to add a spawn location");
					raidSpawnLocations.add(wishWooshXZisLocation(middle.x+gameDistance*i,middle.z+gameDistance*u));
				}
			} 
		}
		
			
		if(raidSpawnLocations.size()==0) //not initialized?
		{
			raidSpawnLocations.add(wishWooshXZisLocation(middle.x, middle.z));
			System.out.println("[killquest]  added to middle smt "+raidSpawnLocations.size());
				
		}	
		else 
		{
			
			System.out.println("[killquest] sides of Chaos Atm "+raidSpawnLocations.size());
		}
		
		raiders = new ArrayList<Creature>();
		raidersThatRide = new ArrayList<Creature>();
		canSpawnRAID =false;
		
		raidInitialTargets = targets;
		
		int actualTier = 0; // this is stupid
		//TODO add an algorythm to this madness
		
		actualTier = getPowerLevelForCurrentRaid();//rides on having the freshest targets on raidInitialTargets
		//balancing
		
		
		chaosOfNature-=actualTier*10;
		
		
		spawnMobAmounts = getRaidMobAmounts(actualTier);
		
		debugTargetingRng = new ArrayList<raiderIsTargetingYouCount>();
		//i dont understand this anymore, but it works and ok
		for(Player p: targets) 
		{
			debugTargetingRng.add(new raiderIsTargetingYouCount(p));	
		}
		
		
		//ok now start spawning shit
		
		int totalSpawns = spawnMobAmounts[0] + spawnMobAmounts[1] + spawnMobAmounts[2] + spawnMobAmounts[3] + spawnMobAmounts[4] + spawnMobAmounts[5] + spawnMobAmounts[6] + spawnMobAmounts[7];
		
		raiderSpawnTypesData = new int[totalSpawns];
		int dumbi=0;
		for(int i=0;i<spawnMobAmounts.length;i++) 
		{
			for(int u=0;u<spawnMobAmounts[i];u++) 
			{
				if(dumbi >= totalSpawns) 
				{
					break;
				}
				raiderSpawnTypesData[dumbi]=i;
				dumbi++;
			} 
		}
		
		
		raidStartSpawning();
					
		 
	}
	
	
	private void addToScore(Integer key, EntityType type) 
	{
		
		if(killquestClearBool[key]==true) 
		{
			//already done with the quest here
			return;
		}
		if(killquestQuests[key]==null) 
		{
			killquestQuests[key] = new int[4];
		}
		
		if(type == EntityType.ZOMBIE || type ==  EntityType.ZOMBIE_VILLAGER|| type == EntityType.HUSK|| type ==EntityType.DROWNED ) 
		{
			killquestQuests[key][1]++;
		}
		if(type == EntityType.CREEPER ) 
		{
			killquestQuests[key][0]++;
		}
		if(type == EntityType.SPIDER ) 
		{
			killquestQuests[key][2]++;
		}
		if(type == EntityType.SKELETON  ||type == EntityType.STRAY) 
		{
			killquestQuests[key][3]++;
		}
		if(checkQuestComplete(key)) 
		{
			Win(key);
		}
	}
	
	private void SpawnARaider(EntityType type,double x, double z) 
	{
		Location spawnHere = rngSpawnForRaiders(x,z,5);
		Creature raider=(Creature)mainWorld.spawnEntity(spawnHere, type);
		raider.setTarget((LivingEntity)rngTargeting());
		
		//System.out.println("[killquest] raider "+raider.hasAI()) ;
		System.out.println("[killquest] raider "+(int )x+" "+(int)z);
		
		
		
		if(raider.hasAI()==false) 
		{
			raider.setAI(true);			
		}
		
		
		raiders.add(raider);
	}
	
	private void SpawnARidingRaider(EntityType type,double x, double z) 
	{
		//TODO find out if a ravager that is riding a ravager really could've happened
		EntityType ravogaroType = EntityType.RAVAGER;
		Location spawnHere = rngSpawnForRaiders(x,z,5);
		Creature ravagaro = (Creature)mainWorld.spawnEntity(spawnHere, ravogaroType);
		Creature rider = (Creature)mainWorld.spawnEntity(spawnHere, type);
		LivingEntity tartget  =(LivingEntity)rngTargeting();
		rider.setTarget(tartget);
		ravagaro.setTarget(tartget);
		ravagaro.addPassenger(rider);
		
		//System.out.println("rider dumb "+rider.hasAI()+ " ravager dumb "+ ravagaro.hasAI()) ;
		
		

		System.out.println("[killquest] riding raider "+(int )x+" "+(int)z);
		raidersThatRide.add(rider);
		raidersThatRide.add(ravagaro);
		
		
	}
	
	private Location rngSpawnForRaiders(double x, double z, int Radius) 
	{
		int rng_x = (int)x;
		int rng_z = (int)z;
		
		if(Radius>0)
		{
			rng_x = rng_x - (int)(Math.random() * Radius * 2)-Radius;
			rng_z = rng_z - (int)(Math.random() * Radius * 2)-Radius;
		}
		
		Location location = mainWorld.getHighestBlockAt(rng_x, rng_z).getLocation();
		location.setY(location.getY()+1);
		return location;
	}
	

	private void raidStartSpawning() 
	{
		System.out.println("spawning raiders");
		raidSpawningTask =new BukkitRunnable() {
			
	        	public void run() 
	        	{
	        		if(raiderSpawnTypesIndex >= raiderSpawnTypesData.length) 
	        		{
	        			System.out.println("[killquest]done spawning "+raiderSpawnTypesData.length);
	        			for(int i =0; i<raiderSpawnTypesData.length;i++) 
	        			{
	        				System.out.println("[killquest] dump SpawnTypes = index;"+i+" value:"+raiderSpawnTypesData[i]);
	        			}
	        			raidStartTargeting();
	        			cancel();
	        		}
        			int currentlySpawning =raiderSpawnTypesIndex;
        			
	        		Location loca = raidSpawnLocations.get((int)(Math.random()*raidSpawnLocations.size()));//spawn
	        		
	        		if(currentlySpawning<5) 
	        		{
	        			SpawnARaider(wishWooshIntisEntityType(raiderSpawnTypesData[currentlySpawning]), loca.getX(), loca.getZ() );
	        			raiderSpawnTypesIndex++;
	        		}
	        		else 
	        		{
	        			SpawnARidingRaider(wishWooshIntisEntityType(raiderSpawnTypesData[currentlySpawning]), loca.getX(), loca.getZ() );
	        			raiderSpawnTypesIndex++;
	        		}
	        		
	        	}
        	};
 		// 20L = 1 Second
        	
    	long rng = (long)(Math.random()*1000);
    	raidSpawningTask.runTaskTimer(this,1L, rng);
	}
	
	
	private Player rngTargeting() 
	{
		if(raidInitialTargets.size() == 0) 
		{
			System.out.println("[Killquest] MASS ive fail, why even spawn if no targets");
			return null;
		}
		
		int rng = (int)(raidInitialTargets.size()*Math.random());
		Player target=raidInitialTargets.get(rng);
		
		if (raidInitialTargets.size() ==1) 
		{
			target = raidInitialTargets.get(0);
		}
		
		
		
		for(raiderIsTargetingYouCount rrr:debugTargetingRng) 
		{
			if(rrr.name() == target.getName()) 
			{
				rrr.plusplus();
				break;
			}
		}
		
		return target;
	}
	
	
	private EntityType wishWooshIntisEntityType(int in) 
	{
		if(in==0) 
		{
			return EntityType.PILLAGER;
		}
		if(in == 1) 
		{
			return EntityType.VINDICATOR;
		}
		if(in == 2) 
		{
			return EntityType.RAVAGER;
		}
		if(in == 3) 
		{
			return EntityType.WITCH;
		}
		if(in == 4) 
		{
			return EntityType.EVOKER;
		}
		if(in == 5) 
		{
			return EntityType.PILLAGER;
		}
		if(in == 6) 
		{
			return EntityType.VINDICATOR;
		}
		if(in == 7) 
		{
			return EntityType.EVOKER;
		}
		
		return EntityType.CAVE_SPIDER;
	}
	
	private Location wishWooshXZisLocation(double x, double z) 
	{
		World w;
		if(mainWorld !=null) 
		{
			w= mainWorld;
		}
		else 
		{
			w = getServer().getWorld("world");
			if(w == null) 
			{
				w = getServer().getWorlds().get(0);
				//System.out.println("not WORLD but "+w.getName());
			}
			if(w!=null) 
			{
				mainWorld=w;
			}
			
		}
		
		double y = w.getHighestBlockYAt((int)x,(int)z);
		return new Location(w,x,y,z);
	}

	
	//runables
	
	//single raiders do targeting here
	private void raidStartTargeting() 
	{		
		//System.out.println("targetting");
		simplierRaiderCount=raiders.size();
		//bugin this "index 20si out of bounds for array size 3" lol total fuckin spaghetth
		//fixed uncancelling
		raidTargetingTask =new BukkitRunnable()
		{
			int currentTargetingDude=0;
			int nullboys=0;
			int AIsgivend=0;
			int targetsAssigned=0;
			public void run() 
	    	{
	    		
	    		if (currentTargetingDude<raiders.size()) 
	    		{
	    			if(raiders.get(currentTargetingDude)!=null) 
	    			{
	    				//same targeting logic
	    				Creature thatboi = raiders.get(currentTargetingDude);
		    			if(thatboi.hasAI()==false) 
		    			{
		    				AIsgivend++;
		    				thatboi.setAI(true);
		    			}
		    			if(thatboi.getTarget() == null) 
		    			{
		    				targetsAssigned++;
		    				thatboi.setTarget(rngTargeting());
		    			}
	    			}
	    			else 
	    			{
	    				nullboys++;
	    			}
	    		}
	    		else 
	    		{
	    			System.out.println("[killquest] finished targeting " + raiders.size());
	    			System.out.println("targets given "+targetsAssigned + " AI's schooled "+ AIsgivend);
	    			if(nullboys>0) 
	    			{
	    				System.out.println("[killquest] bug targetin problems with nulls "+nullboys);
	    			}
	    			
	    			for(Player p:raidInitialTargets) 
    				{
    					for(raiderIsTargetingYouCount rrr:debugTargetingRng) 
    					{
    						if(rrr.name() == p.getName()) 
    						{
    							p.sendMessage(ChatColor.DARK_RED +"you are targeted by "+rrr.getCount()+" raiders");
    							break;
    						}
    					}
    					
    				}
	    			cancel();	
	    			//time to finish up and cancel
	    		}
	    		currentTargetingDude++;
	    		
	    		/*old  n buggy, wont stop running
	    		if(raiders.get(currentTargetingDude)!=null) 
	    		{
	    			Creature thatboi = raiders.get(currentTargetingDude);
	    			if(thatboi.hasAI()==false) 
	    			{
	    				thatboi.setAI(true);
	    			}
	    			if(thatboi.getTarget() == null) 
	    			{
	    				thatboi.setTarget(rngTargeting());
	    			}
	    		}
	    		else 
	    		{
	    			if(currentTargetingDude<=raiders.size()) 
	    			{
	    				for(Player p:raidInitialTargets) 
	    				{
	    					for(raiderIsTargetingYouCount rrr:debugTargetingRng) 
	    					{
	    						if(rrr.name() == p.getName()) 
	    						{
	    							p.sendMessage(ChatColor.DARK_RED +"you are targeted by "+rrr.getCount()+" raiders");
	    							break;
	    						}
	    					}
	    					
	    				}
	    				
	    			}
	    			System.out.println("raid officially started");
	    			cancel();
	    		}
	    		 */
	    	}
    	};
		// 20L = 1 Second
    	raidTargetingTask.runTaskTimer(this,1L, 20L);
	  //Bukkit.getScheduler().runTaskTimer(this, raidSpawningTask,1L, 100L);
		
	}
	
	private int rng(int min, int max) 
	{
		//used in getRaidMobAmounts
		return (int)(Math.random()*(max-min)) +min;
	}

	
//TODO: test these. ar they balanced difficult!?
	private int[] getRaidMobAmounts(int tier) 
	{
		System.out.println("[killquest] requested tier "+tier);
		System.out.println("[tilt quest] was it balanced?");
		// i think 0 through 6 are pretty vanilla, but 7 -9 are something that i enjoy
		//pillager, vindicator, ravager,witch, evoker, and then mixes of ravriders pill,vindic, evoker.
		int[][] amounts = new int[10][];
		amounts[0]= new int[]{rng(4,6),	rng(0,2)	,0	,0			,0		,0	,0	,0};
		amounts[1]= new int[]{rng(3,5),	rng(2,4)	,0	,0			,0		,0	,0	,0};
		amounts[2]= new int[]{rng(3,5),	rng(0,2)	,1	,rng(0,1)	,0		,0	,0	,0};
		amounts[3]= new int[]{rng(4,6),	rng(1,3)	,0	,3			,0		,0	,0	,0};
		amounts[4]= new int[]{rng(4,6),	rng(4,6)	,0	,rng(0,1)	,1		,1	,0	,0};
		amounts[5]= new int[]{rng(4,6),	rng(2,4)	,0	,rng(0,1)	,1		,0	,0	,0};
		amounts[6]= new int[]{rng(2,4),	rng(5,7)	,0	,rng(2,3)	,2		,0	,1	,1};
		amounts[7]= new int[]{rng(2,6),	rng(4,5)	,1	,rng(1,3)	,4		,0	,1	,0};
		amounts[8]= new int[]{rng(2,6),	rng(3,7)	,1	,rng(1,4)	,0		,2	,1	,1};
		amounts[9]= new int[]{rng(2,9),	rng(0,7)	,2	,rng(1,5)	,2		,0	,2	,2};
		if(tier<=9) 
		{
			return amounts[tier];			
		}
		
		//this is a safety net of oopsies i guess.
		return amounts[2];
		
	}
	
	private void doRaid(Player p) 
	{
		if(canSpawnRAID==false) 
		{
			p.sendMessage("the forces of Ill  might be "+(raiders.size()+(raidersThatRide.size()*2))+" around "+lastRaidSpot.getString() );
			return;
		}
		
		Location loca = p.getLocation();
		
		System.out.println(p.getDisplayName() + " wants a raid on " + loca.getX() +" "+loca.getZ());
		ArrayList<Player> singlePlayerCampaign=new ArrayList<Player>();
		singlePlayerCampaign.add(p);
		
		p.sendMessage(ChatColor.DARK_RED + "OK");
		spawnChaos(singlePlayerCampaign);
	}
	

	
	
	
	private boolean playerIsWithinTrackingRange(Player p) 
	{
		double x = p.getLocation().getX();
		if(x > checkTotalQuestRadius) 
		{
			return false;
		}
		if(x < -checkTotalQuestRadius) 
		{
			return false;
		}
		double z = p.getLocation().getZ();
		if(z > checkTotalQuestRadius) 
		{
			return false;
		}
		if(z < -checkTotalQuestRadius) 
		{
			return false;
		}
		return true;
	}
	
	private boolean entityIsWithinTrackingRange(Location loc) 
	{
		double x =  loc.getX();
		if(x > checkTotalQuestRadius) 
		{
			return false;
		}
		if(x < -checkTotalQuestRadius) 
		{
			return false;
		}
		double z = loc.getZ();
		if(z > checkTotalQuestRadius) 
		{
			return false;
		}
		if(z < -checkTotalQuestRadius) 
		{
			return false;
		}
		return true;
	}
	
	private int getLocationsKillQuestIndex(double x , double z) 
	{
		//System.out.println("killquest loc? x int is "+((int)((x+checkTotalQuestRadius)/onePartAffectArea))+" total "+(int)(2*checkTotalQuestRadius/onePartAffectArea*2*checkTotalQuestRadius/onePartAffectArea)+"y"+(int)((z+checkTotalQuestRadius)/onePartAffectArea));
		int theIndexIs = (int)((x+checkTotalQuestRadius)/onePartAffectArea)+(int)(2*checkTotalQuestRadius/onePartAffectArea)*(int)((z+checkTotalQuestRadius)/onePartAffectArea);
		return theIndexIs;
	}
	
	
	private boolean checkQuestComplete(int index) 
	{
		int[] checkthis =killquestQuests[index];
		Integer sum =0;
		for(int i =0;i<checkthis.length ; i++) 
		{
			sum+=checkthis[i];
		}
		
		if (sum < 256) //64*4
		{
			return false;
		}
		if(sum >=1024) 
		{
			return true;
		}
		for(int i =0;i<checkthis.length ; i++) 
		{
			if(checkthis[i]<64) 
			{
				return false;
			}
		}
		return true;
	}
	
	@SuppressWarnings("unused")
	private int CountClearAreas() 
	{
		
		int countSome=0;
		for(int i = 0 ; i<killquestClearBool.length;i++)
		{
			if(killquestClearBool[i]) 
			{
				countSome++;
			}
		}
		
		return countSome;
	}
	
	private void Win(int index) 
	{
		String namesOfTheChapter ="";
		
		
		
		for(Player p : getServer().getOnlinePlayers()) 
		{
			if (getLocationsKillQuestIndex(p.getLocation().getX(), p.getLocation().getZ())==index) 
			{
				namesOfTheChapter += p.getName()+" ";
				p.sendMessage("you win");
			}
		}
		String Date = new java.util.Date().toString();
		
		killquestClearBool[index] = true;
		System.out.println("Quest Complete!!"+killquestQuests[index][0]+" "+killquestQuests[index][1]+" "+killquestQuests[index][2]+" "+killquestQuests[index][3]+" ");
		//killquests.remove(key);
		killquestQuests[index] = new int[1];
		/*
		int localCounter=0;
		for(int i = 0; i<killquestQuests.length ; i++) 
		{
			if(killquestQuests[i].length >1) 
			{
				localCounter ++;
			}
		}*/
		ArrayList<Player> possiblyTargeted= new ArrayList<Player>();
		int sum = 0;
		OfflinePlayer[] opl=getServer().getOfflinePlayers();
		Player biggestScore=opl[0].getPlayer();
		int maxScore=0;
		for(QuesterAndScore qs:ScrollOfQuestingKnights) 
		{
			if(qs.currentIndex == index) 
			{
				
				possiblyTargeted.add(qs.player);
				sum += qs.score;
				if(qs.score>maxScore) 
				{
					maxScore = qs.score;
					biggestScore = qs.player;
				}
				qs.player.sendMessage(ChatColor.GOLD +""+ChatColor.UNDERLINE+ "You win!");
				//qs = new QuesterAndScore(qs.player, index);
			}
		}
		
		
		if(chaosOfNature > sum) 
		{
			//findwhich areas around this one are infested with mobs.
			//todoSpawnRaidImmidiatelly.
			//target playerswho are still questing here.
			spawnChaos(possiblyTargeted);
			chaosOfNature-=sum;			
		}
		else 
		{
			System.out.println("chaos to weak to spawn a raid");
		}
		
		System.out.println("taking away "+sum+" points of Chaos and going to try to Spawn Raid");
		
		
		namingsmbdCouldNameIt=true;
		namingTheOneWhoNames=biggestScore;//player
		namingAreaIndex=index;
		
		namingTheOneWhoNames.sendMessage(ChatColor.GOLD+"you fought valorously");
		namingTheOneWhoNames.sendMessage(ChatColor.GOLD+"name this area as you will");
		namingTheOneWhoNames.sendMessage(ChatColor.GOLD+""+ChatColor.UNDERLINE+"/killquest <area name>");
		
		RememberTheseKnights(biggestScore.getName(), namesOfTheChapter, Date, index);;
		RemoveEveryQuesterFromWonArea(index);//final step
	}
	
	
	private String Progress(int index) 
	{
		int[] checkthis =killquestQuests[index];
		int sumNormal =0;
		int sumTotal=0;
		for(int i =0;i<checkthis.length ; i++) 
		{
			if(checkthis[i]>64) 
			{
				sumNormal+=64;
			}
			else 
			{
				sumNormal+=checkthis[i];
			}
			
			sumTotal+=checkthis[i];
		}
		float SmarterProgress=(float)sumNormal/256f;
		float DumbProgress = (float)sumTotal/1024f;
		System.out.println("smart ="+SmarterProgress);
		System.out.println("dumb ="+DumbProgress);
		if(DumbProgress > SmarterProgress) 
		{
			return progressCompiler((int)(DumbProgress*9));
		}
		else 
		{
			return progressCompiler((int)(SmarterProgress*9));
		}		
	}
	
	private String progressCompiler(int progress) 
	{
		String[] progressbars = new String[9];
		progressbars[0] = ChatColor.GOLD + "<=__ ___ ___>";
		progressbars[1] = ChatColor.GOLD + "<==_ ___ ___>";
		progressbars[2] = ChatColor.GOLD + "<=== ___ ___>";
		
		progressbars[3] = ChatColor.GOLD + "<=== =__ ___>";
		progressbars[4] = ChatColor.GOLD + "<=== ==_ ___>";
		progressbars[5] = ChatColor.GOLD + "<=== === ___>";
		
		progressbars[6] = ChatColor.GOLD + "<=== === =__>";
		progressbars[7] = ChatColor.GOLD + "<=== === ==_>";
		progressbars[8] = ChatColor.GOLD + "<=== === ==::>";
		
		if(progressbars[progress]!=null) 
		{
			return progressbars[progress];			
		}
		else 
		{
			return (ChatColor.MAGIC+"<a=gureoa BUG grb-edsaa>");
		}
	}
	
	

	
	
	//event listeners
	
	@EventHandler
	private void onmobSpawn(EntitySpawnEvent event) 
	{
		if (event == null) 
		{
			System.out.println("event missing. WHAT THE FUCK SPIGOT BUKKIT");
			return;
		}
				
		
		if(event.getLocation().getWorld() != getServer().getWorlds().get(0)) 
		{
			return;
		}
		
		
		
		EntityType type= event.getEntityType();
		EntityType[] filterThis = {EntityType.ZOMBIE,EntityType.SPIDER , EntityType.SKELETON, EntityType.STRAY  ,EntityType.ZOMBIE_VILLAGER ,EntityType.HUSK ,EntityType.DROWNED, EntityType.CREEPER,EntityType.WITCH,EntityType.ENDERMAN };
		
		boolean  goodType=false;
		for (EntityType filter : filterThis) 
		{
			if(filter== type) 
			{
				goodType =true;
				break;
			}
		}
		
		
		if(goodType==false) 
		{
			//System.out.println("[killquest] basic mobs filtered");
			return;
		}
		
		Location loc = event.getEntity().getLocation();
		double x = loc.getX();
		double z = loc.getZ();
			
		int index = getLocationsKillQuestIndex(x,z);
		
		
		
		
		//location scores
		
		
		if(entityIsWithinTrackingRange(loc)) 
		{
			
			try 
			{
					
				if(killquestClearBool[index]==true) 
				{
					event.setCancelled(true);
					
					//System.out.println("cockblockworked");//prints
				}
				else 
				{
					//assume area not clear
					//String outputintotrashcan = ""+event.getEntityType();
					//outputintotrashcan +=" is on index "+index;
					//System.out.println("[killquest] "+outputintotrashcan);
				}
			} 
			catch (NullPointerException e)
			{
				 if(killquestClearBool == null) 
				 {
					 System.out.println("really bad");
				 }
				 else 
				 {
					 System.out.println("whats null in onspawnmob? \n"+ e.getStackTrace().toString());
				 }
				 
			}			
		}
		//could do some ridiculous shit on else part of this method. like if it happens far from spawn, buff the mob and autotarget player.
		
		
		
	}
	
	@EventHandler
	private void onEntityDeath(EntityDeathEvent event) 
	{
		//location scores
		Location loc = event.getEntity().getLocation();
		
		double x = loc.getX();
		double z = loc.getZ();
		
		int index = getLocationsKillQuestIndex(x,z);
		//cheaper
		if(entityIsWithinTrackingRange(loc)==false) 
		{
			return;
		}
		
		if(!(event.getEntity() instanceof Mob) && !(event.getEntity() instanceof Creature)) 
		{
			System.out.println("not mob n not creature");
			return;
			
		}
		
		EntityType type= event.getEntityType();
		EntityType[] filterThis = {EntityType.CREEPER , EntityType.ZOMBIE , EntityType.SKELETON , EntityType.ZOMBIE_VILLAGER , EntityType.HUSK , EntityType.DROWNED ,EntityType.SPIDER};
		
		//filter what to cancel
		boolean  goodType=false;
		for (EntityType filter : filterThis) 
		{
			if(filter== type) 
			{
				goodType =true;
				break;
			}
		}
		
		if(goodType==false) 
		{
			//System.out.println("[killquest] hostiles filtered");//this gets called. nice
			return;
		}
		
		if(entityIsWithinTrackingRange(loc)) 
		{
			addToScore( index, type);
		}
		
		
		if (event.getEntity() instanceof Creature) 
		{
			Creature creature = (Creature)event.getEntity();
			//String deathnotes="creature+dmgCause.eventname \t" +creature.getType() +"+"+creature.getLastDamageCause().getEventName();
			
			if(creature.getKiller() != null) 
			{
				if(creature.getKiller() instanceof Player) 
				{
					
					for(QuesterAndScore p:ScrollOfQuestingKnights) 
					{
						if(p.isThis(((Player)creature.getKiller()).getName())) 
						{
							
							p.AddScore(index, 1);
							break;
						}
					}
					
				}
				else 
				{
					//deathnotes += creature.getKiller().getType();
					chaosOfNature++;//asume creature killing creature or some shit
				}
			}
			if(logMobDeathsToConsole) 
			{
				System.out.println("dead "+creature.getType() +" "+ creature.getLastDamageCause().getEventName());
			}
		}
		
		
		
	}
	
	
	
	
	
	
	//makes file management
	
	
	
	protected String getSaltString() {
        String SALTCHARS = "ᑑ∴ᒷ∷ℸ||⚍╎𝙹!¡ᔑᓭ↸⎓⊣⍑⋮ꖌꖎ⨅/ᓵ⍊ʖリᒲ";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 4) { // length of the random string.
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        String saltStr = salt.toString();
        System.out.println(saltStr);
        return saltStr;

    }

	
	private void doSaveAreaBooleans() 
	{
		
		System.out.println("killquest saving");
		String printthis="";
		int perLine=(int)(checkTotalQuestRadius/onePartAffectArea *2);
		for(int i =0;i<killquestClearBool.length;i++) 
		{
			if(i%perLine==0) 
			{
				printthis+="\n";
			}
			if(killquestClearBool[i]) 
			{
				printthis+="1";	
			}
			else 	
			{
				printthis+="0";
			}
		}
		try{
			//savedData.delete();//deleted
			//replaces it lol
			File savedData = new File(saveFileKillquestAreasCleared);
			
			FileWriter writer = new FileWriter(savedData);
		    writer.write(printthis);
		    writer.close();
		} 
		catch (IOException e) 
		{
		   System.out.println("well that print writer didnt work "+e.toString());
		}
	}

	private void folderCreation() 
    {
    	//System.out.println("what's wrong?" +this.getDataFolder().toString() + "vs."+"plugins");
		File authorDir=new File("plugins"+File.separator+"Lockless");
		if(authorDir.exists()==false) 
		{
			try
			{
				authorDir.mkdirs();
		    }
		    catch(Exception e){
		    	System.out.println("try make authordir fail ");
		    	e.printStackTrace();
		    } 
			if(authorDir.exists()) 
			{
				System.out.println("\nHope you enjoy\n-Lockless\n\n");
			}
		}
		
		pluginFolderPath = new File(authorDir+File.separator+foldername);
		if(pluginFolderPath.exists()==false) 
		{
			try
			{
				System.out.println("plugin "+foldername+ "folder doesnt exist. creating");
				pluginFolderPath.mkdirs();
		    }
		    catch(Exception e)
			{
		    	System.out.println("try make dir fail ");
		    	e.printStackTrace();
		    }
		}
		
		
		saveFileVictory = "plugins"+File.separator+"Lockless"+File.separator+foldername+File.separator+"Victory";
		if(new File(saveFileVictory).exists() == false) 
		{
			try 
			{
				
				FileWriter writer = new FileWriter(new File(saveFileVictory));
			    writer.write("");
			    writer.close();
			}catch (IOException e) {
				System.out.println("clean up on aisle 5");
			}
		}
		
		saveFileAreaNames= "plugins"+File.separator+"Lockless"+File.separator+foldername+File.separator+"AreaNames";
		if(new File(saveFileAreaNames).exists() == false) 
		{
			int LineCount = 900;
			try 
			{
				
				FileWriter writer = new FileWriter(new File(saveFileAreaNames));
				for(int i=0;i<LineCount;i++) 
				{
					String printThis="";
					if(i>=100) 
					{
						printThis += i;
					}
					else if(i>=10) 
					{
		
						printThis += "0"+i;
					}
					else 
					{
		
						printThis +="00"+ i;
					}
					printThis +=" ";
					for(int u=0;u<3;u++) 
					{
						printThis+=getSaltString();
					}
	
					printThis +="\n";
					writer.write(printThis);
				}
			    writer.close();
			}catch (IOException e) {
				System.out.println("clean up on aisle 6");
			}
		}
		
		saveFileKillquestAreasCleared = "plugins"+File.separator+"Lockless"+File.separator+foldername+File.separator+"killquestWorld";
		
		if(new File(saveFileKillquestAreasCleared).exists() == false) 
		{
			FileWriter writer=null;
			try 
			{
				
				writer = new FileWriter(new File(saveFileKillquestAreasCleared));
				//makes a file filled with 0
				String printThis="";
				int howWide = (int)(2*checkTotalQuestRadius/onePartAffectArea);
				int howLong = (int)(2*checkTotalQuestRadius/onePartAffectArea);
				
				
				System.out.println("initializing bools");
				for(int iWide = 0 ;iWide<howWide;iWide++ ) 
				{
					for(int iLong = 0 ;iLong<howLong;iLong++ ) 
					{
						printThis+="0";
					}
					printThis+="\n";
				}
				
				
			    writer.write(printThis);
			
			}catch (IOException e) {
				System.out.println("clean up on aisle 7");
			}
			finally 
			{
				try 
				{
					writer.close();
				}
				catch(IOException e) 
				{
					System.out.println("closing writer"+e.getMessage());
				}
			}
		}
		
		
		System.out.println("SomeLoad from files");
		File areaBools = new File(saveFileKillquestAreasCleared);
		File nameAreas = new File (saveFileAreaNames);
		File Victory = new File(saveFileVictory);
		
		try 
		{
			if(areaBools.exists() == false || nameAreas.exists() == false || Victory.exists() == false) 
			{
				System.out.println("initialLoad cuz not exist on the second check in LOAD");
				
			}
		} 
		catch(NullPointerException e)
		{
			System.out.println("initialLoad cuz null exception");
			
		}
		

				
		//why map anything,array it
		FileReader reader = null;
		BufferedReader buffer=null;
		try
		{
			reader = new FileReader(areaBools);
			buffer=new BufferedReader(reader); 
			String line="";
			int MapIndex = 0;
			
			
			while( (line=buffer.readLine()) != null) 
			{
				for(int i = 0; i <line.length();i++) 
				{
					if(MapIndex>=killquestClearBool.length) 
					{
						System.out.println("text file contained a tad bit more letters than expected. something scuffed");
						
					}
					if(line.charAt(i)=='0') 
					{
						killquestClearBool[MapIndex]=false;
						killquestQuests[MapIndex]=new int[4];
						MapIndex++;
					}
					else if(line.charAt(i)=='1') 
					{
						killquestClearBool[MapIndex]=true;
						killquestQuests[MapIndex]=new int[1];
						MapIndex++;
					}
				}
			}
			
		}
		catch (IOException e) 
		{
		   System.out.println("well that print writer didnt work "+e.toString());
		}
		finally 
		{
		    try 
		    {
		    	reader.close();
		    	buffer.close();
		    }
		    catch (IOException e)
		    {
		    	System.out.println("Exception on closin "+e.getMessage());
		    }
		}
    }
	
	
	
	

	private void RememberTheseKnights(String MainGuy, String TheHelpers, String Date, int index) 
	{
		
		String normalLookingString = "\n"+MainGuy+" and gang "+TheHelpers+" cleared "+index+"\n - "+Date;
		
		if(new File(saveFileVictory).exists()) 
		{
			try 
			{
				
				FileWriter writer = new FileWriter(new File(saveFileVictory));
			    writer.append(normalLookingString);
			    writer.close();
			}catch (IOException e) {
				System.out.println("clean up on aisle 5");
			}
			
		}
		else
		{
			System.out.println("where the fuckign win file\n"+normalLookingString);
		}
	}
	

	

	private void doSaveAreaNamesAll() 
	{
		File namesFile = new File(saveFileAreaNames);
		clearTheFile(saveFileAreaNames);
		FileWriter writer = null;
		try 
		{
			writer = new FileWriter(namesFile);
			writer.write("");
			for (int i = 0; i < areaNames.length ; i++) 
			{
				String lineNumber=""+i;
				if(i<100) 
				{
					lineNumber = "0"+i;
				}
				if(i<10) 
				{
					lineNumber = "00"+i;
				}
				writer.append(lineNumber +" "+areaNames[i]+"\n");
			}
			
		}
		catch (IOException e) 
		{
			System.out.println("clean up on aisle 5");
		}
		finally 
		{
			try 
			{
				writer.close();
				
			}catch (Exception e) {
				System.out.println("close error "+e.getStackTrace());
			}
		}
	}
	
	public static void clearTheFile(String fileName) {
		System.out.println("trying to clear");
		FileWriter fwOb = null;
		PrintWriter pwOb = null;
		try 
		{
			fwOb = new FileWriter(fileName, false); 
			pwOb = new PrintWriter(fwOb, false);
			pwOb.flush();
		}
        catch(IOException e) 
        {
        	System.out.println(" i just dont get deleting file contents i guess");
        }
		finally 
		{
			try 
			{
				pwOb.close();
				fwOb.close();
				
			}catch (IOException e) {
				System.out.println("fucked up closing a filewriters or someshit");
			}
		}
        
    }
	
	private void doSaveAreaNamesAppend(int index, String name) 
	{
		File namesFile = new File(saveFileAreaNames);
		FileWriter writer = null;
		try 
		{
			writer = new FileWriter(namesFile);
		    writer.append(index +" "+name+"\n");
		}catch (IOException e) {
			System.out.println("clean up on aisle 5");
		}
		finally 
		{
			try 
			{
				writer.close();
				
			}
			catch (Exception e) 
			{
				System.out.println("close error "+e.getStackTrace());
			}
		}
	}
	
	private void DoLoadAreaNames() 
	{
		
		

		//int int int " " name
		try
		{
			FileReader reader = new FileReader(new File(saveFileAreaNames));
			BufferedReader buffer=new BufferedReader(reader); 
			String line="";
			
			while( (line=buffer.readLine()) != null) 
			{
				//System.out.println("do smt with :"+line);
				int firstnum = PrepStringToParsing(line);
				
				
				if(firstnum>0 &&firstnum<900) 
				{
					areaNames[firstnum] = line.substring(4);
					//System.out.println("loadkillquest:"+firstnum);
				}
				
			}
			
			buffer.close();
		    reader.close();
		}
		catch (IOException e) 
		{
		   System.out.println("well that print writer didnt work "+e.toString());
		}
	}
	

	
	//addign some stupid reading exceptions i guess
	private int PrepStringToParsing(String unprep) 
	{
		char[] chars= new char[unprep.length()];
		for(int i =0; i<unprep.length();i++) 
		{
			chars[i] = unprep.charAt(i);
		}
		String perp=""+chars[0]+""+chars[1]+""+chars[2];
		int parsed=-10;
		
		if (chars[1]==' ') 
		{
			System.out.println("cought shit 1==' '");
			perp = ""+chars[0];
		}
		if (chars[2]==' ') 
		{
			System.out.println("cought shit 2==' '");
			perp = ""+chars[0]+""+chars[1];
		}
		
		
		
		try 
		{
			
			parsed = Integer.parseUnsignedInt(perp);
		}
		catch (Exception e) 
		{
			System.out.println("exception in int parse "+e.getMessage());
		}
		
		System.out.println("test parsed = " +parsed);
		return parsed;
	}
	
	
	
	
}
