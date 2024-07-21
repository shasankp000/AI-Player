  # A minecraft mod which aims to add a "second player" into the game which will actually be intelligent.

## Ever felt lonely while playing minecraft alone during that two-week phase? Well, this mod aims to solve that problem of loneliness, not just catering to this particular use case, but even (hopefully in the future) to play in multiplayer servers as well.

# Please note that this is not some sort of a commercialised AI product. This project is just a solution to a problem many Minecraft players have faced and do continue to face.

## I had to add that statement up there to prevent misunderstandings.

This mod relies on the internal code of the Carpet mod, please star the repository of the mod: https://github.com/gnembon/fabric-carpet (Giving credit where it's due)

This mod also relies on the ollama4j project. https://github.com/amithkoujalgi/ollama4j

![image](https://github.com/shasankp000/AI-Player/assets/46317225/6b8e22e2-cf00-462a-936b-d5b6f14fb228)

Successfully managed to spawn a "second player" bot.

Added basic bot movement.

[botmovement.webm](https://github.com/user-attachments/assets/c9062a42-b914-403b-b44a-19fad1663bc8)

Progress : 45% 

Implemented basic bot conversation 

[bandicam 2024-07-19 11-12-07-431.webm](https://github.com/user-attachments/assets/556d8d87-826a-4477-9717-74f38c9059e9)



---
# Usage

This section will guide you to setup the mod, since a distributable jar file doesn't exist yet.

Step 1. Download Java 21. 

This project is built on java 21 to support carpet mod's updated API.

Go to: https://bell-sw.com/pages/downloads/#jdk-21-lts

Click on Download MSI and finish the installation process.

![image](https://github.com/user-attachments/assets/8cf3cbe1-91a9-4d7e-9510-84723d928025)


Step 2. Download IntelliJ idea community edition.


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

Step 6. Finally click on the gradle icon again in the right sidebar.

![image](https://github.com/user-attachments/assets/64b085c2-1624-4cfc-9b64-22fd293f1cfe)

Fine the runClient task, and double click it to run minecraft with the mod.



