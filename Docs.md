OpenAI-Compatible Edit
This is commit 9144a26 (Use OpenAI-compatible endpoint by default). Its purpose is to make the mod default to the generic/custom OpenAI-compatible provider instead of Ollama.
Main changes:
Default llmMode changed from ollama to custom in ManualConfig.java.
LLMClientFactory no longer falls back to Ollama for unknown providers. Unsupported modes now return null with an error telling the user to use custom for an OpenAI-compatible endpoint.
Custom provider no longer requires an API key, only a custom API URL. That helps local OpenAI-compatible servers like LM Studio, vLLM, llama.cpp server, etc.
The generic client normalizes base URLs, so users can paste either a base URL or a specific endpoint like /chat/completions, /completions, or /embeddings, and it trims it back to the base.
Authorization headers are only sent when an API key exists.
Several LLM fallback paths, including NLP intent fallback and planner fallback, were moved away from direct Ollama calls and toward LLMClientFactory.
In plain English: the mod is being nudged from “Ollama first, services optional” to “OpenAI-compatible endpoint first, Ollama no longer the universal fallback.



26.2 Port Edit
This is commit 0f54b7b (Port mod to Minecraft Fabric 26.2). It is a large migration commit to make the mod compile/run against the newer Minecraft/Fabric/Carpet API set.
Main changes:
Version bump in gradle.properties:minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT
fabric_version=0.155.2+26.2
mod_version=1.0.5.4-release+26.2

Build system updated in build.gradle:Loom plugin id changed to net.fabricmc.fabric-loom
Shadow plugin changed to com.gradleup.shadow
Java toolchain/release bumped to Java 25
Carpet dependency moved to a Modrinth Maven artifact

Mod metadata now declares Minecraft ~26.2, Java >=25, Fabric API >=0.155.2, and Carpet >=26.2 <26.3 in fabric.mod.json.
A lot of Mojang/Fabric class names and methods were updated from old Yarn-style names to the newer mapped names. Example:ServerPlayerEntity -> ServerPlayer
PlayerEntity -> Player
ItemEntity#getStack() -> ItemEntity#getItem()
getUuid() -> getUUID()
sendMessage(Text.literal(...)) -> sendSystemMessage(Component.literal(...))

Networking payloads migrated from CustomPayload / PacketCodec / PacketByteBuf to CustomPacketPayload / StreamCodec / FriendlyByteBuf.
Some client rendering was stubbed out instead of fully ported. The threat debug renderer is currently disabled pending a 26.2 renderer migration but it works.
