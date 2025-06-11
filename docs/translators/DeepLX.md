# DeepLX

## What is DeepLX?
Refer to the official website:  
[https://deeplx.owo.network/](https://deeplx.owo.network/)

## DeepLX Installation and Usage
Refer to the official documentation:  
[https://deeplx.owo.network/install/](https://deeplx.owo.network/install/)

## DeepLX.yaml Configuration Instructions

```yaml
# DeepLX API address, see: https://deeplx.owo.network/install/variables.html
# Note: For non-local access, ensure devices are on the same LAN with firewall configured, 
# or the device running DeepLX has a public IP
# Example: http://localhost:1188
url: http://localhost:1188

concurrent: 3  # Number of concurrent requests to DeepLX API within one translation request.
               # Requests beyond this number will be queued for processing
```