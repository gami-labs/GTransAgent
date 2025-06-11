# Ollama

## 什么是 Ollama？
Ollama 是一个本地化大模型运行框架，支持在本地计算机上快速部署和运行大型语言模型（如 Llama 3、Mistral、Gemma 等），无需 GPU 也可运行。

## Ollama的安装及使用
参考官方文档[https://github.com/ollama/ollama/blob/main/README.md#quickstart](https://github.com/ollama/ollama/blob/main/README.md#quickstart)

## Ollama.yaml 配置说明

```yaml

# Ollama API地址，参考：https://github.com/ollama/ollama/blob/main/README.md#rest-api
# 注意非本机访问需要确保在同一个局域网，并配置防火墙；或者Ollama运行的设备具有公网IP
url: http://127.0.0.1:11434/api/chat

# 所有你希望使用，并展示到客户端的可选择引擎列表
engineMapping:
  #一个Ollama模型对应一个GTransAgent引擎
  gemma-3-1b: # 引擎在GTransAgent内部的唯一标识，自定义
    name: Gemma 3 1B # 用于在客户端选择私有引擎时显示的名称，自定义
    model: gemma3:1b # 你要使用的Ollama模型名称（包含参数），来源：https://ollama.com/search

 
concurrent: 3  # 一次翻译请求内调用Ollama API的并发请求数，超过这个数量将需要排队处理

# 系统角色提示词，参考： https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion
# {{srcLang}}，{{targetLang}}，{{glossarySensitive}}等为占位符，在运行过程中会被自动替换。
# 可以根据你的需要修改
systemPrompts:

# 用户角色提示词，参考： https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion
# 可以根据你的需要修改
userPrompts:

```

