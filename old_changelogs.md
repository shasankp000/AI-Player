# Below are the changelogs of older versions
---
# Changelog v1.0.3-alpha-2-hotfix-1


Introduced Server sided compatability for training mode.

Play mode for some reason fails to connect to ollama server so I am still working it out, will be done in the next hotfix.

---
## Changelog v.1.0.3-alpha-2

1. Updated the qtable storage format (previous qtable is not comaptible with this version)
2. Created a "risk taking" mechanism which **greatly reduces training time** by making actions taken during training more contextual based instead of random.
3. Added more environment triggers for the bot to look up it's training data, now it also reacts to dangerous enviornment around it, typically lava, places where it might fall off, or sculk blocks.
4. Created very detailed reward mechanisms for the bot during training for the best possible efficiency.
5. Fixed the previous blockscanning code, now replaced using a DLS algorithm for better optimization.

---

## Changelog v.1.0.3-alpha-1

So after a lot of research and painful hours of coding here's the new update!

# What's new :

1. Fixed previous bugs.

2.  Switched to a much lighter model (llama3.2) for conversations, RAG and function calling
  
3. A whole lot of commands
  
4. Reinforcement Learning (Q-learning).
   
5. **Theortically** Multiplayer compatible (just install the dependencies on server side), as carpet mod can run on servers too, but I have not tested it yet. Feedback is welcome from testers on this.
 
6. **Theoretically** the mod **should not** require everyone to install it on multiplayer, it should be a server-sided one, haven't tested this one yet, feedback is welcome from testers.

Bot can now interact with it's envrionment based on "triggers" and then learn about it's situation and try to adapt.

The learning process is not short, don't expect the bot to learn how to deal with a situation very quickly, in fact if you want intelligent results, you may need hours of training(Something I will focus on once I fix some more bugs, add some more triggers and get this version out of the alpha stage)

To start the learning process:

`/bot spawn <botName> training`

Right now the bot only reacts to hostile mobs around it, will add more "triggers" so that the bot responds to more scenarios and learns how to deal with such scenarios in upcoming updates

A recording of what this verison does : 

[![YouTube](http://i.ytimg.com/vi/6zEORx1OKfA/hqdefault.jpg)](https://www.youtube.com/watch?v=6zEORx1OKfA)

---

## New commands :

Spawn command changed.

`/bot spawn <bot> <mode: training or play>`, if you type anything else in the mode parameter you will get a message in chat showing the correct usage of this command


`/bot use-key <W,S, A, D, LSHIFT, SPRINT, UNSNEAK, UNSPRINT> <bot>`

`/bot release-all-keys <bot> <botName>`

`/bot look <north, south, east, west>`


`/bot detectDangerZone` // Detects lava pools and cliffs nearby

`/bot getHotBarItems` // returns a list of the items in it's hotbar

`/bot getSelectedItem` // gets the currently selected item

`/bot getHungerLevel` // gets it's hunger levels

`/bot getOxygenLevel` // gets the oxygen level of the bot

`/bot equipArmor` // gets the bot to put on the best armor in it's inventory

`/bot removeArmor` // work in progress.

---

## What to do to setup this version before playing the game :

```
1. Make sure you still have ollama installed.
2. In cmd or terminal type `ollama pull nomic-embed-text (if not already done).
3. Type `ollama pull llama3.2`
4. Type `ollama rm gemma2 (if you still have it installed)
5. Type `ollama rm llama2 (if you still have it installed)
6. If you have run the mod before go to your .minecraft folder, navigate to a folder called config, and delete a file called settings.json5
```

Then make sure you have turned on ollama server. After that launch the game.

Type `/configMan` in chat and select llama3.2 as the language model, then hit save and exit.

Then type `/bot spawn <yourBotName> <training (for training mode, this mode won't connect to language model) and play (for normal usage)`

---

For the nerds : How does the bot learn?

It uses an algorithm called Q-learning which is a part of reinforcement learning.

A very good video explanation on what Q-learning is :

[Reinforcement learning 101](https://www.youtube.com/watch?v=vXtfdGphr3c)



---

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

To top it all off, Gemma 2 8b is the best performing model for this mod right now, so I will suggest y'all to use gemma2.

In fact some of the methods won't even run without gemma2 like the RAG for example so it's a must.

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



