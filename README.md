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
# Download links

1. From this github page, just download from the releases section or follow the steps in usage section to build and test.
2. Modrinth: https://modrinth.com/mod/ai-player/ [Recommended as the github version is often unstable]
3. Curseforge: Will upload soon after a few more patches and updates.

---

# Progress: 60%

## For the nerds

Sucessfully implemented the intellgence update.

So, for the tech savvy people, I have implemented the following features.

**LONG TERM MEMORY**: This mod now features concepts used in the field AI like Natural Language Processing (much better now) and something called

**Retrieval Augmented Generation (RAG)**.

How does it work?

Well:

![Retrieval Augmented Generation process outline](https://cdn.modrinth.com/data/cached_images/f4f51461946d8fb02be131d6ea53db238cdbd8c4.png)

![Vectors](https://media.geeksforgeeks.org/wp-content/uploads/20200911171455/UntitledDiagram2.png)


We convert the user input, to a set of vector embeddings which is a list of numbers.

Then **physics 101!**

A vector is a representation of 3 coordinates in the XYZ plane. It has two parts, a direction and a magnitude.

If you have two vectors, you can check their similarity by checking the angle between them.

The closer the vectors are to each other, the more **similar** they are!

Now if you have two sentences, converted to vectors, you can find out whether they are similar to each other using this process.

In this particular instance I have used a method called **cosine similarity**

[Cosine similarity](https://www.geeksforgeeks.org/cosine-similarity/)

Where you find the similarity using the formula

`(x, y) = x . y / |x| . |y|`

where |x| and |y| are the magnitudes of the vectors.


So we use this technique to fetch a bunch of stored conversation and event data from an SQL database, generate their vector embeddings, and then run that against the user's prompt. We get then further sort on the basis on let's say timestamps and we get the most relevant conversation for what the player said.


Pair this with **function calling**. Which combines Natural Language processing to understand what the player wants the bot to do, then call a pre-coded method, for example movement and block check, to get the bot to do the task.

Save this data, i.e what the bot did just now to the database and you get even more improved memory!

To top it all off, Llama 2 is the best performing model for this mod right now, so I will suggest y'all to use llama2.

In fact some of the methods won't even run without llama2 like the RAG for example so it's a must.

---

![image](https://github.com/shasankp000/AI-Player/assets/46317225/6b8e22e2-cf00-462a-936b-d5b6f14fb228)

Successfully managed to spawn a "second player" bot.

Added basic bot movement.

[botmovement.webm](https://github.com/user-attachments/assets/c9062a42-b914-403b-b44a-19fad1663bc8)


Implemented basic bot conversation 

[bandicam 2024-07-19 11-12-07-431.webm](https://github.com/user-attachments/assets/556d8d87-826a-4477-9717-74f38c9059e9)


Added a mod configuration menu (Still a work in progress)


https://github.com/user-attachments/assets/5ed6d6cf-2516-4a2a-8cd2-25c0c1eacbae

**Implemented intermediate XZ pathfinding for the bot**


https://github.com/user-attachments/assets/687b72a2-a4a8-4ab7-8b77-7373d414bb28

**Implemented Natural Language Processing for the bot to understand the intention and context of the user input and execute methods**
Can only understand if you want the bot to go some coordinates.

https://vimeo.com/992051891?share=copy

**Implemented nearby entity detection**



https://github.com/user-attachments/assets/d6cd7d86-9651-4e6f-b14a-56332206a440




---
# Usage

## If you want to manually build and test this project, follow from step 1.

## For playing the game, download the jar file either from modrinth or the releases section and go directly to step 6.

**For users who have used the mod before, transitioning to version 1.0.2-hotfix-2**

```
1. Go to your game folder (.minecraft)/config and you will find a settings.json5 file.
Delete that

2.(If you have run the previous 1.0.2 version already then) again go back to your .minecraft. you will find a folder called "sqlite_databases". Inside that is a file called memory_agent.db

3. Delete that as well.
4. Install the models, mistral, llama2 and nomic-embed-text

5.Then run the game
6. Inside the game run /configMan to set the language model to llama2.

Then spawn the bot and start talking!
```

---
# Buidling the project from intellij

Step 1. Download Java 21. 

This project is built on java 21 to support carpet mod's updated API.

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


Step 6. Setup ollama.

Go to https://ollama.com/

![image](https://github.com/user-attachments/assets/c28798e4-c7bf-4faf-88e5-76315f88f0d1)

Download based on your operating system.

After installation, run ollama from your desktop. This will launch the ollama server. 

This can be accessed in your system tray

![image](https://github.com/user-attachments/assets/3ed6468e-0e8c-4723-ac80-1ab77a7208d4)


Now open a command line client, on windows, search CMD or terminal and then open it.

Depending on your specs, you can download a language model and then configure in the project to use it.

For now, in the terminal, type ![image](https://github.com/user-attachments/assets/7ecf3eea-2a7c-481b-a914-53678081e60e)

~~In this example we are going with the phi3 model.~~

For the updated version 1.0.2, you will need to download the `llama2` model: `ollama pull llama2` 
And another model `nomic-embed-text`: `ollama pull nomic-embed-text`

Without llama2, intelligence will be very low
Without nomic-embed-text, the mod will crash.


I do intend to add an option in the mod to change and configure models within the game GUI.

Once done. Go to the next step.

Step 7. Go to the files section

![image](https://github.com/user-attachments/assets/443b12ad-cb8c-4049-8fa3-01875023c42a)

Click on the ollamaClient 

Go to line 199.

[recording1](https://github.com/user-attachments/assets/bd01e7a7-ef5f-4379-9157-965c97b85ce3)

Remove the current model type, then follow the video and set it to `OllamaModelType.PHI3`

Although intelliJ autosaves but press `CTRL+S` to save.

Step 8. Finally click on the gradle icon again in the right sidebar.

![image](https://github.com/user-attachments/assets/64b085c2-1624-4cfc-9b64-22fd293f1cfe)

Fine the runClient task, and double click it to run minecraft with the mod.

---
# Mod usage

This section is to describe the usage of the mod in-game

## Commands

*Main command*

`/bot`

Sub commands: 

`spawm <botName>` This command is used to spawn a bot with the desired name. ==For testing purposes, please keep the bot name to Steve==.

`walk <botName> <till>` This command will make the bot walk forward for a specific amount of seconds.

`/goTo <botName> <x> <y> <z>` This command is supposed to make the bot go to the specified co-ordinates, by finding the shortest path to it. It is still a work in progress as of the moment.

`/sendAMessage <botName> <message>` This command will help you to talk to the bot.

`/teleportForward <botName>` This command will teleport the bot forward by 1 positive block

`/testChatMessage <botName>` A test command to make sure that the bot can send messages.

**Example Usage:**

`/bot spawn Steve`


