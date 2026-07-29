# Official website

https://shasankp000.github.io/AI-Player-Website/

---

## Star History

<a href="https://www.star-history.com/?repos=shasankp000%2FAI-Player&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=shasankp000/AI-Player&type=date&theme=dark&legend=top-left&sealed_token=NHxEX7xorfiwx4C0j3yqbODkU0soNOMoSoiPFcOSXU-vXuriAf78CDQNHLeUpWseX3nJ6letJNCgXvW5FSSWus_3fMM_vDAMDA1d9i94C7hy--YsZ2h-uUPM5M-OONUZp7K7Jip98dknD-tpaZvc3IhE54JxF8qcFWi-8w4I-q9-3Q_aojkERbOlfqkM" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=shasankp000/AI-Player&type=date&legend=top-left&sealed_token=NHxEX7xorfiwx4C0j3yqbODkU0soNOMoSoiPFcOSXU-vXuriAf78CDQNHLeUpWseX3nJ6letJNCgXvW5FSSWus_3fMM_vDAMDA1d9i94C7hy--YsZ2h-uUPM5M-OONUZp7K7Jip98dknD-tpaZvc3IhE54JxF8qcFWi-8w4I-q9-3Q_aojkERbOlfqkM" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=shasankp000/AI-Player&type=date&legend=top-left&sealed_token=NHxEX7xorfiwx4C0j3yqbODkU0soNOMoSoiPFcOSXU-vXuriAf78CDQNHLeUpWseX3nJ6letJNCgXvW5FSSWus_3fMM_vDAMDA1d9i94C7hy--YsZ2h-uUPM5M-OONUZp7K7Jip98dknD-tpaZvc3IhE54JxF8qcFWi-8w4I-q9-3Q_aojkERbOlfqkM" />
 </picture>
</a>

---

# Read this section please.

This project so far is the result of thousands of hours of endless reasearch, trials and errors, and just the simple goal of eliminating loneliness from minecraft as much as possible.
If you liked my work, please consider donating so that I can continue to work on this project in peace and can actually prove to my parents that my work is making a difference. (Also not having to ask for pocket money from mom).

Just know that I won't ever give up on this project.

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://buymeacoffee.com/shasankp000)

I also have started accepting payments and donations in cryptocurrencies.

Ethereum: 0x47014CC9F8054593027c53996ddfDFa4ca8a5271

Bitcoin: bc1qae6u7rqv0pmppxmr2uqfftrj4p6c7hmm3m39r5

Solana: 3U3bXZJ2NrMV9FmkaotvPaNwWthg68sRqvstJwanyhcU

Polygon Network (USDC or any other polygon based coins): 0x47014CC9F8054593027c53996ddfDFa4ca8a5271

Any amount would help :)

---
# Also, THIS!

NLP pipeline training in-depth information : https://github.com/shasankp000/NLP_2.0_pipeline

Website repo: https://github.com/shasankp000/AI-Player-Website

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
2. Modrinth: https://modrinth.com/mod/ai-player/
3. Curseforge: https://www.curseforge.com/minecraft/mc-mods/ai-player

---

# Progress: 75.5%

---
# Current stage of the mod

Since the new semester in my college has started this month, I am gonna be under really heavy pressure, since I have to study 11 subjects in total (Machine learning, Linear Algebra, Physics, CyberSecurity, and a lot more lol). Don't worry though I won't stop working on this project, it's just that the updates will be quite slower.

It's understandable if y'all leave by then or give up on this project, so, I won't mind. :))

Thank you all from the core of my heart for the support so far. I never imagined we would come this far.

Latest Update: 21/8/2025 at 04:08 AM IST (Indian Standard Time)


Please have patience while waiting for updates. Since I am the only guy working on this project, it does take me time to address all the issues/add in new features.

---
# Changelog v1.0.5.2-release+1.21.1

**Important: Earlier proposed second part features will be added in the next patch and this new update marks the end of support for 1.20.6 and below.**

Thanks to https://github.com/arichornlover AI Player has now been updated to version 1.21.1

This minor update of AI player updates the mod to version 1.21.1 and also brings support for other custom API providers.

## Custom OpenAI-Compatible Provider Support

This feature allows you to use alternative AI providers that are compatible with the OpenAI API standard, such as OpenRouter, TogetherAI, Perplexity, and others.

## How to Use

### 1. Enable Custom Provider Mode

Set the system property(JVM argument) when launching the game:
```
-Daiplayer.llmMode=custom
```

### 2. Configure API Settings

0. Firstly, delete the existing settings.json5 in the config folder (save any api keys elsewhere for the meantime)
1. Open the in-game API Keys configuration screen (`/configMan`) and then click the API Keys button.
2. Set the following fields:
    - **Custom API URL**: The base URL of your provider (e.g., `https://openrouter.ai/api/v1`)
    - **Custom API Key**: Your API key for the provider
3. Hit save. In case you don't see the list of models immediately hit the "Refresh Models" button once or twice.
4. If you still don't see the list of models, close the config manager, type `/configMan` again and you should see the list of models available.
5. ollama is still required to be open in the background because of the embedding model being used, but I will separate this entirely in the next mini patch, by adding an embedding api endpoint from the providers, and also upgrading to the `embedddinggemma` model from `nomic-embed-text`

### 3. Select a Model

The system will automatically fetch available models from your provider's `/models` endpoint and display them in the model selection interface.

## Supported Providers

Any provider that implements the OpenAI API standard should work. Some examples:

- **OpenRouter**: `https://openrouter.ai/api/v1`
- **TogetherAI**: `https://api.together.xyz/v1`
- **Perplexity**: `https://api.perplexity.ai/`
- **Groq**: `https://api.groq.com/openai/v1`
- **Local LM Studio**: `http://localhost:1234/v1`

## API Compatibility

The custom provider implementation uses the following OpenAI API endpoints:

- `GET /models` - For fetching available models
- `POST /chat/completions` - For sending chat completion requests

Your provider must support these endpoints with the same request/response format as OpenAI's API.

## Troubleshooting

- **"Custom provider selected but no API URL configured"**: Make sure you've set the Custom API URL field
- **"Custom API key not set in config!"**: Make sure you've set the Custom API Key field
- **Empty model list**: Check that your API key is valid and the URL is correct
- **Connection errors**: Verify that the provider URL is accessible and supports the OpenAI API format

---

# Changelog v1.0.5.1-release+1.20.6-bugfix-2

- Fixed a lot of bugs that got overlooked in the previous testing phase.


---

# Changelog v1.0.5.1-release+1.20.6-bugfix

I realized that many of the commitments made in the previous announcement were too ambitious to implement in a single update. To keep development smooth, this release is the **first part** of a two-part update.  

This update focuses on **core system rewrites** and **better AI decision-making**, laying the foundation for the next wave of features.  

Also support for versions below 1.20.6 has been dropped due to codebase changes that I simply can't handle migrating by myself. However others are free to port to lower versions.

Upcoming second part will have the update in 1.20.6 as the final update for 1.20.6 and also will have a new update to directly 1.21.6 where hence the future versions will continue onwards.

---

## What's New in 1.0.5.1

### Revamped NLP System  
- Fully redesigned Natural Language Processing (NLP) — no more *"I couldn’t understand you."*  
- This is a **new and experimental system** I’ve been designing and rigorously testing over the last month.  
- Results are promising, but not yet up to my personal standards — expect further refinements in future updates.  

### Rewritten RAG & Database System (with Web Search)  
- New Retrieval-Augmented Generation (RAG) system integrated with a database and **web search**.  
- The AI now provides **accurate factual information about Minecraft**, drastically reducing hallucinations.  
- Supported search providers:  
  - Gemini API  
  - Serper API  
  - Brave Search API (in development, will push this to the next patch instead) 

### Meta-Decision Layer  
- Added a **task chaining system**:  
  - You give a high-level instruction → the bot automatically breaks it into smaller tasks → executes step by step.  

#### Current Supported Tasks:  
- Go to a location  
- Go to a location and mine resources  
- Detect nearby blocks & entities  
- Report stats (health, oxygen, hunger, etc.)  

### API Integrations  
AI Player now supports multiple LLM providers (configurable via API keys):  
- **OpenAI**  
- **Anthropic Claude**  
- **Google Gemini**  
- **xAI Grok**  
- **Custom OpenAI-Compatible Providers** (e.g., OpenRouter, TogetherAI, Perplexity)

#### Using Custom Providers

To use a custom OpenAI-compatible provider:

1. **Set the provider mode**: Add `-Daiplayer.llmMode=custom` to your JVM arguments
2. **Configure in-game**: Open the API Keys screen and set:
   - Custom API URL (e.g., `https://openrouter.ai/api/v1`)
   - Custom API Key (your provider's API key)
3. **Select model**: Choose from the available models fetched from your provider

See [CUSTOM_PROVIDERS.md](CUSTOM_PROVIDERS.md) for detailed instructions and supported providers.  

### Web Search Tool

If you select the Gemini Search as the web search tool for the LLM, it will use the API key you have set as your LLM provider in the settings.json5 file automatically.

For https://serper.dev/ search, get an api key from serper.dev and then navigate to the config folder in game, open the `ai_search_config.json` and put the key:

![Web search json file contents](https://cdn.modrinth.com/data/cached_images/b3dbb07a9e166d4d0860d490d8d5d938e4e6cd50.png)

*(Note: I couldn’t test all of these myself except the Gemini API since API keys are costly, but the integrations are ready.)*  

---
# Bugfixes

- Fixed bug where JVM arguments were not being read.
- Removed owo-lib. AI-Player now uses an in-house config system.
- Fixed API keys saving issues.
- Added a new Launcher Detection System. Since Modrinth launcher was conflicting by it's own variables path system so the QTable was not being loaded. Supports: Vanilla MC Launcher, Modrinth App, MultiMC, Prism Launcher, Curseforge launcher, ATLauncher, and even unknown launchers which would be unsupported by default, assuming they follow the vanilla mc launcher's path schemes.
- Revamped the Config Manager UI with a responsive UI along with a search option for providers with a lot of models (like gemini).

![Config Manager New UI with search options and responsive UI](https://cdn.modrinth.com/data/cached_images/e5ab3e3d23978a96312c6528fd27f996d279adcc_0.webp)

---

## Development Notes  
- While this update may look small on the surface, designing the systems, writing the code, and debugging took **a huge amount of time**.  
- On top of this, I’ve picked up more freelance contracts and need to focus on my final-year project.  
- Updates will continue — just at a slower pace.  

---

## Coming Soon in Part 2  

Here’s what’s planned for the **next patch**:  

- **Combat & Survival Enhancements**  
  - Bot uses weapons (including ranged) to fend off mobs.  
  - Reflex module upgrades.  
  - More natural world interactions (e.g., sleeping at night).
  - A more lightweight but more powerful logic engine that will replace the current LLM based reasoning for the Meta-Decision Layer  

- **Improved Path Tracer**  
  - Smarter navigation through **water and complex terrain**. Abilities such as bridging upwards with blocks.

- **Self-Goal Assignment System**  
  - Bot assigns itself goals like a real player.  
  - Will initiate conversations with players and move autonomously.  

- **Mood System** *(design phase)*  
  - Adds emotional context and varied behavior.  

- **Player2 Integration**  
  - Highly requested — this will be the first major feature of the second update.  

---

# Upcoming changes (some of them might be seen in the second patch).

1. Switch to Deep-Q learning instead of traditonal q-learning (TLDR: use a neural network instead of a table)
2. Create custom movement code for the bot for precise movement instead of carpet's server sided movement code.
3. Implement human consciousness level reasoning??? (to some degree maybe) (BIG MAYBE)

---

# Current bugs in this version :

I can proudly say that all bugs in this current version, has been for good, squashed.

---
## Some video footage of this version

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


