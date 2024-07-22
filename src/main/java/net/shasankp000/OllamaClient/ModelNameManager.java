package net.shasankp000.OllamaClient;

import io.github.amithkoujalgi.ollama4j.core.types.OllamaModelType;

public class ModelNameManager {
    public static String getModelType(String modelName) {
        switch (modelName) {
            case "gemma": return OllamaModelType.GEMMA;
            case "gemma2": return OllamaModelType.GEMMA2;
            case "llama2": return OllamaModelType.LLAMA2;
            case "llama3": return OllamaModelType.LLAMA3;
            case "mistral": return OllamaModelType.MISTRAL;
            case "mixtral": return OllamaModelType.MIXTRAL;
            case "llava": return OllamaModelType.LLAVA;
            case "llava-phi3": return OllamaModelType.LLAVA_PHI3;
            case "neural-chat": return OllamaModelType.NEURAL_CHAT;
            case "codellama": return OllamaModelType.CODELLAMA;
            case "dolphin-mixtral": return OllamaModelType.DOLPHIN_MIXTRAL;
            case "mistral-openorca": return OllamaModelType.MISTRAL_OPENORCA;
            case "llama2-uncensored": return OllamaModelType.LLAMA2_UNCENSORED;
            case "phi": return OllamaModelType.PHI;
            case "phi3": return OllamaModelType.PHI3;
            case "orca-mini": return OllamaModelType.ORCA_MINI;
            case "deepseek-coder": return OllamaModelType.DEEPSEEK_CODER;
            case "dolphin-mistral": return OllamaModelType.DOLPHIN_MISTRAL;
            case "vicuna": return OllamaModelType.VICUNA;
            case "wizard-vicuna-uncensored": return OllamaModelType.WIZARD_VICUNA_UNCENSORED;
            case "zephyr": return OllamaModelType.ZEPHYR;
            case "openhermes": return OllamaModelType.OPENHERMES;
            case "qwen": return OllamaModelType.QWEN;
            case "qwen2": return OllamaModelType.QWEN2;
            case "wizardcoder": return OllamaModelType.WIZARDCODER;
            case "llama2-chinese": return OllamaModelType.LLAMA2_CHINESE;
            case "tinyllama": return OllamaModelType.TINYLLAMA;
            case "phind-codellama": return OllamaModelType.PHIND_CODELLAMA;
            case "openchat": return OllamaModelType.OPENCHAT;
            case "orca2": return OllamaModelType.ORCA2;
            case "falcon": return OllamaModelType.FALCON;
            case "wizard-math": return OllamaModelType.WIZARD_MATH;
            case "tinydolphin": return OllamaModelType.TINYDOLPHIN;
            case "nous-hermes": return OllamaModelType.NOUS_HERMES;
            case "yi": return OllamaModelType.YI;
            case "dolphin-phi": return OllamaModelType.DOLPHIN_PHI;
            case "starling-lm": return OllamaModelType.STARLING_LM;
            case "starcoder": return OllamaModelType.STARCODER;
            case "codeup": return OllamaModelType.CODEUP;
            case "medllama2": return OllamaModelType.MEDLLAMA2;
            case "stable-code": return OllamaModelType.STABLE_CODE;
            case "wizardlm-uncensored": return OllamaModelType.WIZARDLM_UNCENSORED;
            case "bakllava": return OllamaModelType.BAKLLAVA;
            case "everythinglm": return OllamaModelType.EVERYTHINGLM;
            case "solar": return OllamaModelType.SOLAR;
            case "stable-beluga": return OllamaModelType.STABLE_BELUGA;
            case "sqlcoder": return OllamaModelType.SQLCODER;
            case "yarn-mixtral": return OllamaModelType.YARN_MISTRAL;
            case "nous-hermes2-mixtral": return OllamaModelType.NOUS_HERMES2_MIXTRAL;
            case "samantha-mistral": return OllamaModelType.SAMANTHA_MISTRAL;
            case "stablelm-zephyr": return OllamaModelType.STABLELM_ZEPHYR;
            case "meditron": return OllamaModelType.MEDITRON;
            case "wizard-vicuna": return OllamaModelType.WIZARD_VICUNA;
            case "stablelm2": return OllamaModelType.STABLELM2;
            case "magicoder": return OllamaModelType.MAGICODER;
            case "yarn-llama2": return OllamaModelType.YARN_LLAMA2;
            case "nous-hermes2": return OllamaModelType.NOUS_HERMES2;
            case "deepseek-llm": return OllamaModelType.DEEPSEEK_LLM;
            case "llama-pro": return OllamaModelType.LLAMA_PRO;
            case "open-orca-platypus2": return OllamaModelType.OPEN_ORCA_PLATYPUS2;
            case "codebooga": return OllamaModelType.CODEBOOGA;
            case "mistrallite": return OllamaModelType.MISTRALLITE;
            case "nexusraven": return OllamaModelType.NEXUSRAVEN;
            case "goliath": return OllamaModelType.GOLIATH;
            case "nomic-embed-text": return OllamaModelType.NOMIC_EMBED_TEXT;
            case "notux": return OllamaModelType.NOTUX;
            case "alfred": return OllamaModelType.ALFRED;
            case "megadolphin": return OllamaModelType.MEGADOLPHIN;
            case "wizardlm": return OllamaModelType.WIZARDLM;
            case "xwinlm": return OllamaModelType.XWINLM;
            case "notus": return OllamaModelType.NOTUS;
            case "duckdb-nsql": return OllamaModelType.DUCKDB_NSQL;
            case "all-minilm": return OllamaModelType.ALL_MINILM;
            case "codestral": return OllamaModelType.CODESTRAL;
            default:
                throw new IllegalArgumentException("Unknown model name: " + modelName);
        }
    }
}
