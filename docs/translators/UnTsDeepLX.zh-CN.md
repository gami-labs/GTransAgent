# UnTsDeepLX

## 什么是 UnTsDeepLX？
参考官网(https://github.com/un-ts/UnTsDeepLX)[https://github.com/un-ts/UnTsDeepLX]

## UnTsDeepLX的安装及使用
你可以直接使用 https://deeplx.vercel.app/translate 无需部署。

你也可以自行部署，参考官方文档[https://github.com/un-ts/UnTsDeepLX](https://github.com/un-ts/UnTsDeepLX)

## UnTsDeepLX.yaml 配置说明

```yaml

# UnTsDeepLX API地址
# 免部署直接使用：https://deeplx.vercel.app/translate
# 自行部署：http://localhost:1188
url: https://deeplx.vercel.app/translate

concurrent: 3  # 一次翻译请求内调用 UnTsDeepLX API的并发请求数，超过这个数量将需要排队处理

```

