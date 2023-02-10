# Pool
> A pterodactyl templating engine for dynamically scaling servers

Pool can be used for many things, such as dynamically scaling minecraft servers for [Multipaper](https://multipaper.io) or scaling shards for a gameserver system.

Pool uses pterodactyl as a way to create services & store the main repository for templates.


## Pterodactyl Setup

To setup pterodactyl to be ready to use pool you need to setup your templating server.

### Templating Repository

The templating repository is where all templates are stored and is where you can create/edit them.

To set up the templating repository you need to create a server in your pterodactyl instance named `[pvphub-pool]`, we have [created a egg](https://raw.githubusercontent.com/PvPHubLLC/pooldt/main/egg.json) for this repository, make sure to use it.

The disk space should be atleast 5GBs or more, it depends on your templates sizes.
After the egg is done setting up, you should see 2 folders in your server:
```
compiled
templates
```
The `compiled` folder is a internal folder, you dont need to touch it. `templates` is where templates are stored. If you used the  egg, you should see a example template.

The structure of the templates folder:
```
template-id | Template server contents
template-id.json | Template Metadata
```

The metadata is the templates settings, here is the format:
```json
{
    "id": "hello", // Template ID must match with file name and folder name
    "pterodactyl": {
        "nest": 0, // Nest ID
        "egg": 1,  // Egg ID, Keep in mind the server files from the egg will still be there.
        "env": { // (Optional) Server Environment Variables 
            "BRANCH": "main"
        },
        "allocationIps": [ // (Optional) Allowed allocation IPs
            "172.18.0.1"
        ],
        "startupCmd": "echo Hello world from template!" // (Optional) Startup Command
    },
    "ram": 1024,
    "storage": 4096,
    "cpu": 200
}
```

### Nodes

Pool identifies pool nodes by the node description containing `[pool-node]`, pool nodes are where services are created.
Pool won't work without at least 1 pool node setup.

## Getting Started with the Library

To get started, add the [PvPHub Maven](https://maven.pvphub.me/private) repository to your repositories
```kts
repositories {
    maven {
        name = "pvphub-private"
        url = uri("https://maven.pvphub.me/private")
        credentials {
            username = System.getenv("PVPHUB_MAVEN_USERNAME")
            password = System.getenv("PVPHUB_MAVEN_SECRET")
        }
    }
} 
```
and add the pool dependency
```kts
dependencies {
    implementation("co.pvphub:pool:1.0")
}
```
then you should have pool added to your project.

### Service API
The service API is used to create/remove services dynamically.

You need this API to run for the Plugin API, you need a client api key and a application api key for this.

To get started:
```kt
fun main() {
    val pool = Pool.get("https://panel.my.host", "app-token", "client-token") // Initialize pool
    val svc = pool.createService(pool.getTemplateById("hello")) // Create a service via the template "hello"
    sleep(5000) // wait 5s
    svc.end() // destroy service
    pool.clearServices(pool.getTemplateById("hello"), 5) // remove 5 services that are currently running "hello" as a template
}
```

### Plugin API

The plugin API is a way to interact or create modules for pool 
that will integrate pool into other applications such as [Velocity](https://velocitypowered.com)

We have some builtin module(s) that you can add

##### Velocity
```kt
PluginManager.addPlugin(VelocityPoolPlugin(proxyServer))
```

#### Custom Modules
To create your very own module, follow this guide:

First you want to make a plugin class such as ``HelloPoolPlugin``
```kt
class VelocityPoolPlugin(private val proxyServer: ProxyServer): PoolPlugin() {
    val runningServices = mutableMapOf<Pool.Service, ServerInfo>()
    override fun onServiceCreate(s: Pool.Service) {
        val alloc = s.server.getSocketAddress() // get the allocation address
        val info = ServerInfo(
            "${s.template.id}-${pool.getServicesRunningByTemplate(s.template).size + 1}",
            alloc
        ) // create a serverinfo object for velocity
        proxyServer.registerServer(info) // register that service into velocity
        runningServices += s to info // add for future tracking
    }

    override fun onServiceEnd(s: Pool.Service) {
        val info = runningServices[s] // get the serverinfo for that service
        proxyServer.unregisterServer(info) // unregister the server
        runningServices -= s // remove it from the map
    }
}
```
