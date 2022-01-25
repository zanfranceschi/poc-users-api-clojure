# poc-users-api

That's a simple API I've coded to learn Clojure. Please, don't use it as a reference as I'm learning it and am sure I've done a lot of bad things :)

## Requirements

### Datomic

https://www.datomic.com/get-datomic.html ─ Started version will do.


### Authorization Server/Service

I've used [OAuth0](https://manage.auth0.com/) and it's cool :)

Get token example request with OAuth0:

```
curl -v -XPOST \
   -H "Content-Type: application/json" \
   -d '{"client_id":"00", "client_secret":"00","grant_type":"client_credentials","audience":"00"}' \
      https://[YOUR-SUBDOMAIN-HERE].auth0.com/oauth/token
```

## Testing

I've made a simple bash script that runs unit tests (not needed at all):

```
./run-tests.sh
``` 