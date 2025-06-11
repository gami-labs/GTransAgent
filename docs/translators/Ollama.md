# Ollama

## What is Ollama?
Ollama is a localized large model operation framework that supports rapid deployment and operation of large language models (such as Llama 3, Mistral, Gemma, etc.) on local computers, and can run without GPU support.

## Ollama Installation and Usage
Refer to the official documentation:  
[https://github.com/ollama/ollama/blob/main/README.md#quickstart](https://github.com/ollama/ollama/blob/main/README.md#quickstart)

## Ollama.yaml Configuration Instructions

```yaml
# Ollama API address, see: https://github.com/ollama/ollama/blob/main/README.md#rest-api
# Note: For non-local access, ensure devices are on the same LAN with firewall configured, 
# or the device running Ollama has a public IP
url: http://127.0.0.1:11434/api/chat

# List of all engines you want to use and display as selectable options to clients
engineMapping:
  # Each Ollama model corresponds to one GTransAgent engine
  gemma-3-1b: # Unique identifier for the engine within GTransAgent (customizable)
    name: Gemma 3 1B # Display name when selecting private engine in client (customizable)
    model: gemma3:1b # Ollama model name to use (including parameters), source: https://ollama.com/search

concurrent: 3  # Number of concurrent requests to Ollama API within one translation request. 
               # Requests beyond this number will be queued.

# System role prompts, see: https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion
# Placeholders like {{srcLang}}, {{targetLang}}, {{glossarySensitive}} will be automatically replaced during runtime.
# Can be modified according to your needs
systemPrompts:

# User role prompts, see: https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion
# Can be modified according to your needs
userPrompts:
```