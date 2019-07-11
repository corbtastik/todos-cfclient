# Todo(s) Tool

This is a sample Spring Shell application that uses the Cloud Foundry Java Client library to deploy Todo apps to PCF.  Simply put this app is a codification of deploying 3 different apps that work together as one to PCF.

A Todo App has 3 parts:

1. [Todos Edge](https://github.com/corbtastik/todos-edge)
2. [Todos API](https://github.com/corbtastik/todos-api)
3. [Todos UI](https://github.com/corbtastik/todos-webui)

The edge serves as a client access point and handles Todo app routing (not platform routing), the API is simply an abstraction for middleware and by default will save "todos" to memory, the UI is a Vue.js application vendored as a Boot app.  They work together to form a sample "Todo app" and this tool automates the deployment to PCF.  You don't need this tool to push the apps to PCF, they can be cloned, built, configured and pushed with ease.  This is simply a handy tool and modest sample.

## Using

The best way to get setup locally is to run ``make setup`` which will clone the Edge, API and UI repos into this projects directory under .apps

1. You need to clone [the Tool repo](https://github.com/corbtastik/todos-tool)
1. Then clone each repo listed above for [Edge](https://github.com/corbtastik/todos-edge), [API](https://github.com/corbtastik/todos-api) and [UI](https://github.com/corbtastik/todos-webui)
1. Build each application
1. Configure the Tool
1. Run the Tool
1. Stamp out Todo apps on PCF

## Configure Todos Tool

The main configuration needed is where to find the jars for the Edge, API and UI apps.  This can be anywhere on the file-system, for example if you ran ``make setup`` this will be the directory structure.

```bash
# projects
./todos-cfclient/
./todos-webui/
./todos-api/
./todos-edge/
# edge, api and ui jar files in here
./jars/
```

```
jars:
  folder: /Users/corbs/dev/github/todos-apps/jars
cf:
  api: api.sys.retro.io
  username: corbs
  password: changeme-or-pass-cli-arg
  organization: retro
  space: arcade
  domain: apps.retro.io
  skipSslValidation: true
```

## Run Todos Tool

