## apiKey Application Addresses
* OpenAI [https://platform.openai.com/](https://platform.openai.com/)

* AliyunBailian [https://bailian.console.aliyun.com/](https://bailian.console.aliyun.com/)

* Anthropic [https://www.anthropic.com/claude](https://www.anthropic.com/claude)

* BigModel [https://bigmodel.cn/usercenter/proj-mgmt/apikeys](https://bigmodel.cn/usercenter/proj-mgmt/apikeys)

* DeepSeek [https://platform.deepseek.com/api_keys](https://platform.deepseek.com/api_keys)

* Gemini [https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)

* Mistral [https://console.mistral.ai/api-keys](https://console.mistral.ai/api-keys)

* VolcengineArk [https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey](https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey)


## YAML Configuration Instructions

```yaml

# Usually, you can keep the default value
url: 

# Enter the apiKey you applied for
apiKey:

# All selectable engines you want to use and display to the client
engineMapping:
  # Each model corresponds to a GTransAgent engine
  gpt-4o-mini: # Unique identifier for the engine within GTransAgent, customizable
    name: Gpt 4 mini # Name displayed to the client when selecting a private engine, customizable
    model: gpt-4o-mini # The model name you want to use, must match the provider

  # You can continue to add more models...
 
concurrent: 3  # Number of concurrent requests to the Ollama API per translation request; requests exceeding this will be queued

# System prompt, reference: https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion
# {{srcLang}}, {{targetLang}}, {{glossarySensitive}}, etc. are placeholders and will be automatically replaced at runtime.
# You can modify as needed
systemPrompts:

# User prompt, reference: https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion
# You can modify as needed
userPrompts:


##
#    Note: The prompts must ensure the translation input and output formats are as follows:
#
#    Input format:
#    [{"id":1,"text":"Source Text"}]
#
#    Output format:
#    {"translations":[{"id":1,"text":"Translated Text"}]}
#
#    Glossary format:
#      {
#        "test": "检测",
#        "internet": "互联网"
#      }
#
##


```