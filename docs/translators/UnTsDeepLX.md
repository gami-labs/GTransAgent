# UnTsDeepLX

## What is UnTsDeepLX?
Refer to the official website:  
[https://github.com/un-ts/UnTsDeepLX](https://github.com/un-ts/UnTsDeepLX)

## UnTsDeepLX Installation and Usage
You can directly use https://deeplx.vercel.app/translate without deployment.

You can also deploy it yourself. Refer to the official documentation:  
[https://github.com/un-ts/UnTsDeepLX](https://github.com/un-ts/UnTsDeepLX)

## UnTsDeepLX.yaml Configuration Instructions

```yaml
# UnTsDeepLX API address
# Use without deployment: https://deeplx.vercel.app/translate
# Self-deployment: http://localhost:1188
url: https://deeplx.vercel.app/translate

concurrent: 3  # Number of concurrent requests to UnTsDeepLX API within one translation request.
               # Requests beyond this number will be queued for processing
```