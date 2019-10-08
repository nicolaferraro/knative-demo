# Knative Demo

This demo shows the main features of Knative (Serving and Eventing).

## Requirements

This demo requires:
- [Minikube 1.4.0](https://github.com/kubernetes/minikube/releases/tag/v1.4.0)
- [Istio 1.3.0](https://github.com/istio/istio/releases/tag/1.3.0)
- [Knative Serving 0.9.0](https://github.com/knative/serving/releases/tag/v0.9.0)
- [Knative Eventing 0.9.0](https://github.com/knative/eventing/releases/tag/v0.9.0)
- [Knative Eventing Contrib 0.9.0](https://github.com/knative/eventing-contrib/releases/tag/v0.9.0) (Only the CamelSource)
- [Camel K 1.0.0-M2](https://github.com/apache/camel-k/releases/tag/1.0.0-M2)

Follow the instructions on the official [Knative docs](https://knative.dev/docs/install/knative-with-minikube/) for installing both 
Istio and Knative.

Follow the official [Camel K docs](https://camel.apache.org/camel-k/latest/installation/minikube.html) for installing it on Minikube.

## Demo

### 1. Creating your first service

This repo includes a Quarkus service that just echoes the received messages.

To run it:

```
cd quarkus
./mvnw quarkus:dev
```

To test the service:

```
http :8080 hello=world
```

You can use `curl` or `wget` instead of `HTTPie` if you prefer.

The service should print `Received: {"hello": "world"}` in its own logs.

To deploy the service on Kubernetes, you need to create a container image first (or you can skip this, since I've done it for you):

```
./mvnw clean install
docker build -f src/main/docker/Dockerfile.jvm -t nferraro/rest-json-jvm .
```

There's also a Dockerfile for native compilation in case you want to try it.

After the image is ready, you can proceed to create the service.

```
kubectl apply -f quarkus.yaml
```

To verify that the service is running correctly, you can send a HTTP request through the service mesh.

First, get the host:port of the ingress gateway:

```
MESH=$(minikube ip):$(kubectl get svc istio-ingressgateway --namespace istio-system --output 'jsonpath={.spec.ports[?(@.port==80)].nodePort}')
```

Then send a request directed to the service:

```
http $MESH Host:quarkus.default.example.com hello=world
```

### 2. Send some events to the service

We'll now create a event source based on Apache Camel.

```
kubectl apply -f chuck-source.yaml
```

This translates into a Camel K integration that produces events about Chuck Norris every 10 seconds.

### 3. Injecting the default broker

Assuming you're connected to the Kubernetes cluster and to the "default" namespace, the first thing you've to do is to inject a 
default broker into the namespace.

```
kubectl label namespace/default knative-eventing-injection=enabled
```

If everything is working correctly, you should see two new pods being created:

```
kubectl get pod
```

Typical result:
```
NAME                                     READY   STATUS              RESTARTS   AGE
default-broker-filter-77cf985f4-2tqf8    0/1     ContainerCreating   0          10s
default-broker-ingress-b7b769bc7-gldjw   0/1     ContainerCreating   0          10s
```

Those are technical endpoints needed for the broker to work. If you query the broker, you should find it ready after the two pods start.

```
kubectl get broker
```

You should see something like:
```
NAME      READY   REASON   HOSTNAME                                   AGE
default   True             default-broker.default.svc.cluster.local   102s
```

A broker named "default" has been installed in the current namespace and it's ready to be used.

### 4. Target Chuck source to the broker

You can now change the sink of the chuck-source to target the default broker:

```
# Uncomment the broker sink and comment the service part in chuck-source.yaml
kubectl apply -f chuck-source.yaml
```


### 5. Deploy the Bot source

Another source available is container in the `bot-source.yaml` file which relays messages from a Telegram bot.

Refer to the [Telegram Bot Father](https://telegram.me/BotFather) for how to create such bot and get the authorization token.

You should edit the `bot-source.yaml` file to set the authorization token for your own bot.

Once done, deploy it through:

```
kubectl apply -f bot-source.yaml
```

### 6. Play with triggers

You can create some triggers to see how the service gets called.

```
kubectl apply -f trigger-bot.yaml
kubectl apply -f trigger-chuck.yaml
```

You can also delete them and recreate, or also change the filter of the bot trigger to filter
only messages from a particular author.

### 7. Bonus: reply to the chat

We're going to create a Camel K "exporter" that will reply back to the Telegram chat with an echo message.

Exporters are not part of Knative, but they can easily be created with Camel K.

Edit the `echo.groovy` file to include your Telegram authorization token, then:

```
kamel run echo.groovy
```

This will create a knative service named "echo".

To connect the service to the event mesh, we can create a trigger:

```
kubectl apply -f trigger-bot-echo.yaml
```

Now each message that you send to the bot will get a reply from a serverless service.
