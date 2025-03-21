## Stargazers over time
[![Stargazers over time](https://starchart.cc/shasankp000/AI-Player.svg?variant=adaptive)](https://starchart.cc/shasankp000/AI-Player)

---

# Read this section please.

This project so far is the result of thousands of hours of endless reasearch, trials and errors, and just the simple goal of eliminating loneliness from minecraft as much as possible.
If you liked my work, please consider donating so that I can continue to work on this project in peace and can actually prove to my parents that my work is making a difference. (Also not having to ask for pocket money from mom).

Just know that I won't ever give up on this project.

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://buymeacoffee.com/shasankp000)

## Paypal

[https://paypal.me/shasankp000](https://paypal.me/shasankp000)

---
# Also, THIS!

If anyone is interested on the underlying algorithms I am working on for increased **intelligence** for the minecraft bot, feel free to check out this repository: 

https://github.com/shasankp000/AI-Tricks

I am open to suggestions/improvements, if any. (Obviously there will be improvements from my own end).

  
---

# Project description

---

**The footages for the bot conversation and config manager here in the github page is a bit outated. Check the modrinth page and download the mod to stay updated.** 
  
A minecraft mod which aims to add a "second player" into the game which will actually be intelligent.

**Ever felt lonely while playing minecraft alone during that two-week phase? Well, this mod aims to solve that problem of loneliness, not just catering to this particular use case, but even (hopefully in the future) to play in multiplayer servers as well.**

**Please note that this is not some sort of a commercialised AI product. This project is just a solution to a problem many Minecraft players have faced and do continue to face.**

I had to add that statement up there to prevent misunderstandings.

This mod relies on the internal code of the Carpet mod, please star the repository of the mod: https://github.com/gnembon/fabric-carpet (Giving credit where it's due)

This mod also relies on the ollama4j project. https://github.com/amithkoujalgi/ollama4j

---

# Current bugs in this version :

1. Play mode doesn't work on servers (yet) (to be fixed in hotfix-2).


# Download links

1. From this github page, just download from the releases section or follow the steps in usage section to build and test.
2. Modrinth: https://modrinth.com/mod/ai-player/
3. Curseforge: https://www.curseforge.com/minecraft/mc-mods/ai-player

---

# Progress: 71%

The 1.20.6 port is out now!

# Changelog v1.0.4-release+1.20.6

- Updated codebase for 1.20.6 compatibility.
- Optimized codebase by removing redundant codes and unused imports.

(previous version 1.0.4-beta-1 fixed server sided compatibility fully)


---

# Upcoming changes.

1. Introduce goal based reinforcement learning.
2. Switch to Deep-Q learning instead of traditonal q-learning (TLDR: use a neural network instead of a table)
3. Create custom movement code for the bot for precise movement instead of carpet's server sided movement code.
4. Give the bot a sense of it's surroundings in more detail (like how we can see blocks around us in game) so that it can take more precise decisions. Right now it has knowledge of what blocks are around it, but it doesn't know how those blocks are placed around it, in what order/shape. I intend to fix that. 
5. Implement human consciousness level reasoning??? (to some degree maybe) (BIG MAYBE)

---
## Some video footage of this version (version 1.0.3-alpha-2)

`mob related reflex actions`

https://github.com/user-attachments/assets/1700e1ff-234a-456f-ab37-6dac754b3a94


`environment reaction`


https://github.com/user-attachments/assets/786527d3-d400-4acd-94f0-3ad433557239

---
# Usage

If you want to manually build and test this project, follow from step 1.

For playing the game, download the jar file either from modrinth or the releases section and go directly to step 6.

---
# Buidling the project from intellij

Step 1. Download Java 21. 

This project is built on java 17 to support carpet mod's updated API.

Go to: https://bell-sw.com/pages/downloads/#jdk-21-lts

Click on Download MSI and finish the installation process. [Windows]

![image](https://github.com/user-attachments/assets/8cf3cbe1-91a9-4d7e-9510-84723d928025)

**For linux users, depending on your system install openjdk-21-jdk package.**


Step 2. Download IntelliJ idea community edition.

https://www.jetbrains.com/idea/download/?section=windows

![Screenshot 2024-07-21 123239](https://github.com/user-attachments/assets/75d636cb-99f8-4966-8a18-f9ae22ce46bc)

Step 3. Download the project. 

If you have git setup in your machine already you can just clone the project to your machine and then open it in intellij

Or alternatively download it as a zip file, extract it and then open it in intellij

![image](https://github.com/user-attachments/assets/4384fa90-2fe9-4685-a793-8238f2789532)

Step 4. Configure the project SDK.

![image](https://github.com/user-attachments/assets/ee5a1be5-7fa4-4d42-bfdd-291a74666267)

Click on the settings gear.

![image](https://github.com/user-attachments/assets/ef74de58-6e97-428a-9e76-c5c19423963b)

Go to Project Structure

![image](https://github.com/user-attachments/assets/8979a760-3a96-49a6-8a42-c8dcd4c2e0ee)

Configure the SDK here, set it to liberica 21


Step 5. Once done wait for intellij to build the project sources, this will take a while as it basically downloads minecraft to run in a test version.

If you happen to see some errors, go to the right sidebar, click on the elephant icon (gradle)

![image](https://github.com/user-attachments/assets/7916d2bf-1381-4f9e-9df6-1e43a7bfed55)

And click on the refresh button, besides the plus icon.
Additionally you can go to the terminal icon on the bottom left

![image](https://github.com/user-attachments/assets/f95f54ab-847a-42de-b3d8-4401f03ac83a)

And type `./graldew build`

---

**Below instructions are same irrespective of build from intellij or direct mod download.**

Step 6. Setup ollama.

Go to https://ollama.com/

![image](https://github.com/user-attachments/assets/c28798e4-c7bf-4faf-88e5-76315f88f0d1)

Download based on your operating system.

After installation, run ollama from your desktop. This will launch the ollama server. 

This can be accessed in your system tray

![image](https://github.com/user-attachments/assets/3ed6468e-0e8c-4723-ac80-1ab77a7208d4)


Now open a command line client, on windows, search CMD or terminal and then open it.

```
1. In cmd or terminal type `ollama pull nomic-embed-text (if not already done).
2. Type `ollama pull llama3.2`
3. Type `ollama rm gemma2 (if you still have it installed) (for previous users only)
4. Type `ollama rm llama2 (if you still have it installed) (for previous users only)
5. If you have run the mod before go to your .minecraft folder, navigate to a folder called config, and delete a file called settings.json5 (for previous users only)
```

Then **make sure you have turned on ollama server**. 

Step 7: Download the dependencies

Step 8: Launch the game.

Step 9: Type `/configMan` in chat and select llama3.2 as the language model, then hit save and exit.

Step 10: Then type `/bot spawn <yourBotName> <training (for training mode, this mode won't connect to language model) and play (for normal usage)`

---
# Mod usage

This section is to describe the usage of the mod in-game

## Commands

**Main command**

`/bot`

Sub commands: 

`spawm <bot>` This command is used to spawn a bot with the desired name.

`walk <bot> <till>` This command will make the bot walk forward for a specific amount of seconds.

`go_to <bot> <x> <y> <z>` This command is supposed to make the bot go to the specified co-ordinates, by finding the shortest path to it. It is still a work in progress as of the moment.

`send_message_to <bot> <message>` This command will help you to talk to the bot.

`teleport_forward <bot>` This command will teleport the bot forward by 1 positive block

`test_chat_message <bot>` A test command to make sure that the bot can send messages.

`detect_entities <bot> A command which is supposed to detect entities around the bot`

`use-key <W,S, A, D, LSHIFT, SPRINT, UNSNEAK, UNSPRINT> <bot>`

`release-all-keys <bot> <botName>`

`look <north, south, east, west>`

`detectDangerZone` // Detects lava pools and cliffs nearby

`getHotBarItems` // returns a list of the items in it's hotbar

`getSelectedItem` // gets the currently selected item

`getHungerLevel` // gets it's hunger levels

`getOxygenLevel` // gets the oxygen level of the bot

`equipArmor` // gets the bot to put on the best armor in it's inventory

`removeArmor` // work in progress.

**Example Usage:**

`/bot spawn Steve training`

The above command changes credits go to [Mr. Álvaro Carvalho](https://github.com/A11v1r15)

And yes since this mod relies on carpet mod, you can spawn a bot using carpet mod's commands too and try the mod. But if you happen to be playing in offline mode, then I recommend using the mod's in built spawn command.


