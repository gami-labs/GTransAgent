# DeepLX

## 什么是 DeepLX？
参考官网(https://deeplx.owo.network/)[https://deeplx.owo.network/]

## DeepLX的安装及使用
参考官方文档[https://deeplx.owo.network/install/](https://deeplx.owo.network/install/)

## DeepLX.yaml 配置说明

```yaml

# DeepLX API地址，参考：https://deeplx.owo.network/install/variables.html
# 注意非本机访问需要确保在同一个局域网，并配置防火墙；或者DeepLX运行的设备具有公网IP
url: http://localhost:1188

concurrent: 3  # 一次翻译请求内调用 DeepLX API的并发请求数，超过这个数量将需要排队处理

```

