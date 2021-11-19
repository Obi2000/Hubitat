import groovy.transform.Field

definition(
    name: 'Govee Integration',
    namespace: 'Obi2000',
    author: 'Obi2000',
    description: 'Integrates with Govee',
    category: 'My Apps',
    iconUrl: '',
    iconX2Url: '',
    iconX3Url: ''
)

@Field GoveeAPI = GoveeAPI_create();
@Field Utils = Utils_create();
@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[0]

//  ===== Settings =====
//TODO: Move out into helper library
private final ENDPOINT_DEVICES_V1() { '/v1/devices' }
private getAPIKey() { settings.APIKey }

//  ===== Lifecycle methods ====
def installed() {
    Utils.toLogger("info", "installing Govee App")
    synchronizeDevices();
}

def uninstalled() {
    Utils.toLogger("info", "uninstalled Govee App")
    deleteChildDevicesByDevices(getChildDevices());
}

def updated() {	
    Utils.toLogger("info", "updating with settings")
    synchronizeDevices();
}

//  ===== Helpers =====

def getGoveeAPI() {
    return GoveeAPI;
}

def getUtils() {
    return Utils;
}

//  ===== Pages =====
preferences {
    page(name: "pageIntro")
    page(name: "pageDevices")
}

def pageIntro() {
    Utils.toLogger("debug", "Showing Introduction Page");

    return dynamicPage(
        name: 'pageIntro',
        title: 'Govee Introduction', 
        nextPage: 'pageDevices',
        install: false, 
        uninstall: true) {
        section("""\
                    |This application connects to the Govee service.
                    |It will allow you to add your Govee devices from Govee within Hubitat.
                    |
                    |Govee API Key can be requested from the Govee App, on the about us page.
                    |An email will be sent to you with the Api Key, just copy it and paste it down below.
                    |""".stripMargin()) {}
            section('Enter your Govee Details.') {
                input name: 'APIKey', title: 'User API Key', type: 'text', required: true
       			input "logLevel", "enum", title: "Log Level", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
            }
            section('''\
                    |Press 'Next' to connect to your Govee Account.
                    |'''.stripMargin()) {}
    }
}

def pageDevices() {
    def goveeDevices = []
    GoveeAPI.getDevices() { devices -> goveeDevices = devices}
    def deviceList = [:]
	state.foundDevices = []
	goveeDevices.each {
        deviceList << ["${it.device}":"${it.deviceName} - ${it.model} (${it.device})"]
		state.foundDevices << [device: it.device, deviceName: it.deviceName, model: it.model, controllable: it.controllable, retrievable: it.retrievable]
	}
    return dynamicPage(
        name: 'pageDevices', 
        title: 'Govee Devices',
        install: true, 
        uninstall: true) {
        section() {
            paragraph 'Select the following devices';
            input name: 'devices', title: 'Select Devices', type: 'enum', required: true, multiple:true, options: deviceList
        }
    }
}

// ==== App behaviour ====

def synchronizeDevices() {
    def childDevices = getChildDevices();
    def childrenMap = childDevices.collectEntries {
        [ "${it.deviceNetworkId}": it ]
    };

    for (goveeDeviceId in settings.devices) {
        def goveeDevice = state.foundDevices.find({it.device == goveeDeviceId})        
        def hubitatDeviceId = goveeIdToDeviceNetworkId(goveeDevice.device, goveeDevice.model);

        if (childrenMap.containsKey(hubitatDeviceId)) {
            childrenMap.remove(hubitatDeviceId)
            continue;
        }
        
        //Utils.toLogger("info", "goveeDeviceId: ${goveeDeviceId}")
        //Utils.toLogger("info", "goveeDevice: ${goveeDevice}")
        //Utils.toLogger("info", "goveedeviceName: ${goveeDevice.deviceName}")
        //Utils.toLogger("info", "model: ${goveeDevice.model}")
        device = addChildDevice('Obi2000', 'Govee Immersion LED Strip', hubitatDeviceId, 
                                [name: "Govee Immersion LED Strip", label: "Govee $goveeDevice.deviceName"]);
    }

    deleteChildDevicesByDevices(childrenMap.values());
}

def deleteChildDevicesByDevices(devices) {
    for (d in devices) {
        deleteChildDevice(d.deviceNetworkId);
    }
}

def goveeIdToDeviceNetworkId(macAddr, model) {
    return "${macAddr}-${model}"
}

def getMacAddr(device) {
    def (String macAddr, String model) = device.deviceNetworkId.split('-', 2)
    return macAddr
}

def getModel(device) {    
    def (String macAddr, String model) = device.deviceNetworkId.split('-', 2)
    return model
}

/**
 * 
 * The Groove API
 *
 */
def GoveeAPI_create(Map params = [:]) {
    def defaultParams = [
		uri: 'https://developer-api.govee.com'
    ]

    def resolvedParams = defaultParams << params;
    def apiUrl = resolvedParams['uri']

    def instance = [:];

    def authHeaders = {
        return ['Govee-API-Key': settings.APIKey, 'Content-Type': "application/json"]
    }

    def apiGet = { path, query, closure ->
        Utils.toLogger("debug", "API Get Request to Govee with path $path")
        
        def options = [
                 uri: apiUrl,
                'path': path,
                'headers': authHeaders()
        ]        

        if (query) {
            options['query'] = query
        }

        //Utils.toLogger("debug", "options: $options")

        return httpGet(options) { resp -> 
            closure.call(resp.data);
        }
    };

    def apiPut = { path, body, closure ->
        Utils.toLogger("debug", "API Get Request to Govee with path $path")
        
        def options = [
                 uri: apiUrl,
                 'path': path,
                 'headers': authHeaders(),
                 contentType: "application/json",
                 body: body
        ]

        //Utils.toLogger("debug", "options: $options")

        return httpPut(options) { resp -> 
            closure.call(resp.data);
        }
    };

    instance.getDevices = { closure ->
        Utils.toLogger("info", "Retrieving devices from Govee")
        apiGet("${ENDPOINT_DEVICES_V1()}", "") { resp ->
            closure.call(resp.data.devices)
        }
    };

    instance.getDeviceSupport = { macAddr, model, closure ->
        Utils.toLogger("info", "Retrieving device support '$macAddr' - '$model' from Govee")
        apiGet("${ENDPOINT_DEVICES_V1()}", [device: macAddr, model: model]) { resp ->
            closure.call(resp.data.devices[0])
        }
    };

    instance.getDeviceState = { macAddr, model, closure ->
        Utils.toLogger("info", "Retrieving device state '$macAddr' - '$model' from Govee")
        apiGet("${ENDPOINT_DEVICES_V1()}/state", [device: macAddr, model: model]) { resp ->
            closure.call(resp.data.properties)
        }
    };

    instance.sendCommand = { macAddr, model, command, payload, closure ->
        Utils.toLogger("info", "Sending command '$macAddr' - '$model' - '$command' - '$payload' to Govee")
        apiPut("${ENDPOINT_DEVICES_V1()}/control", [device: macAddr, model: model, cmd: ["name": command, "value": payload]]) { resp ->
            closure.call(resp.data)
        }
    };

    return instance;
}

/**
 * Simple utilities for manipulation
 */
def Utils_create() {
    def instance = [:];

    instance.toLogger = { level, msg ->
        if (level && msg) {
            Integer levelIdx = LOG_LEVELS.indexOf(level);
            Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel);
            if (setLevelIdx < 0) {
                setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL);
            }
            if (levelIdx <= setLevelIdx) {
                log."${level}" "${app.name} ${msg}";
            }
        }
    }
    
    return instance;
}
