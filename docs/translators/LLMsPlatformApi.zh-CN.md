## apiKey 申请地址
* OpenAI [https://platform.openai.com/](https://platform.openai.com/)

* AliyunBailian (阿里云百炼) [https://bailian.console.aliyun.com/](https://bailian.console.aliyun.com/)

* Anthropic [https://www.anthropic.com/claude](https://www.anthropic.com/claude)

* BigModel (智谱AI) [https://bigmodel.cn/usercenter/proj-mgmt/apikeys](https://bigmodel.cn/usercenter/proj-mgmt/apikeys)

* DeepSeek [https://platform.deepseek.com/api_keys](https://platform.deepseek.com/api_keys)

* Gemini [https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey)

* Mistral [https://console.mistral.ai/api-keys](https://console.mistral.ai/api-keys)

* VolcengineArk (火山方舟) [https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey](https://console.volcengine.com/ark/region:ark+cn-beijing/apiKey)

* xAI (Grok) [https://docs.x.ai/docs/resources/faq-api/accounts](https://docs.x.ai/docs/resources/faq-api/accounts)

## yaml 配置说明

```yaml

# 一般保持默认值即可
url: 

# 填入你申请的apiKey
apiKey:

# 所有你希望使用，并展示到客户端的可选择引擎列表
engineMapping:
  # 一个模型对应一个GTransAgent引擎
  gpt-4o-mini: # 引擎在GTransAgent内部的唯一标识，自定义
    name: Gpt 4 mini # 用于在客户端选择私有引擎时显示的名称，自定义
    model: gpt-4o-mini # 你要使用的模型名称，必须与服务提供商一致

  # 你可以添加更多模型 ...
 
concurrent: 3  # 一次翻译请求内调用Ollama API的并发请求数，超过这个数量将需要排队处理

# 系统角色提示词，参考： https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion
# {{srcLang}}，{{targetLang}}，{{glossarySensitive}}等为占位符，在运行过程中会被自动替换。
# 可以根据你的需要修改
systemPrompts:

# 用户角色提示词，参考： https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion
# 可以根据你的需要修改
userPrompts:


##
#    注意提示词必须确保翻译输入输出格式如下：
#
#    输入格式:
#    [{"id":1,"text":"Source Text"}]
#
#    输出格式:
#    {"translations":[{"id":1,"text":"Translated Text"}]}
#
#    词汇表格式:
#      {
#        "test": "检测",
#        "internet": "互联网"
#      }
#
##


```

