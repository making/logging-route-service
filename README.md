# Logging Route Service

CF [Route Service](https://docs.cloudfoundry.org/services/route-services.htmlgi) that logs http request and response including bodies.


## Deploy

```
mvn clean package -DskipTests=true
cf push
cf create-user-provided-service logging-svc -r https://logging-svc.cfapps.io
``` 

## Bind the route service

```
cf bind-route-service cfapps.io logging-svc --hostname your-app
```

## Unbind the route service

```
cf unbind-route-service cfapps.io logging-svc --hostname your-app
```

## Test locally

```
$ curl localhost:8080 -d foo=bar -H "X-CF-Forwarded-Url: http://httpbin.org/post" -H "X-CF-Proxy-Metadata: a" -H "X-CF-Proxy-Signature: a"
```