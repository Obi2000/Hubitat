// Hubitat driver for Govee RGB Strips using Cloud API
// Version 1.1.1
//
// 2021-01-07 -	Improved robustness of recalling device state
//				Fixed error with inital hue/sat commands if no data returned from server
// 2021-01-11 - Added option to use a 0-254 brightness range expected by some strips
// 2021-02-11 - API Key Alone generates device list, Then auto sets to your preferences last device in list
// 2021-03-29 - Added setColorTemperature(k,l,d) command for Hubitat 2.2.6, bug fixes in state reporting
// 2021-11-18 - Make the installation easier by adding an app that can create the govee devices automatically

import groovy.transform.Field

@Field Utils = Utils_create();
@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[0]

metadata {
	definition(name: "Govee Immersion LED Strip", namespace: "Obi2000", author: "Obi2000") {
		capability "Switch"
		capability "ColorControl"
		capability "ColorTemperature"
		capability "Light"
		capability "SwitchLevel"
		capability "ColorMode"
		capability "Refresh"
		
		attribute "colorName", "string"
        
        command "setColorTemperature", [[name:"Color temperature*", type:"NUMBER", description:"Color temperature in degrees Kelvin", constraints:["NUMBER"]]]
//		command "white"
//		command "ModeMusic"
//		command "ModeVideo"
//		command "DeviceInfo"
    }

	preferences {		
		section("Device Info") {
			input(name: "aRngBright", type: "bool", title: "Alternate Brightness Range", description: "For devices that expect a brightness range of 0-254", defaultValue: false)
   			input "logLevel", "enum", title: "Log Level", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
		}
	}
}

def on() {
	sendEvent(name: "switch", value: "on")
	sendCommand("turn", "on")
}

def off() {
	sendEvent(name: "switch", value: "off")
	sendCommand("turn", "off")
}

def setColorTemperature(value, level = null, dur = null)
{
    if(value > 9000) {
        value = 9000
    }
    
    if(value < 2000) {
        value = 2000
    }
    
    sendEvent(name: "colorMode", value: "CT")
    //Utils.toLogger("debug", "ColorTemp = ${value}")
    def intvalue = value.toInteger()
    sendEvent(name: "switch", value: "on")	
    sendEvent(name: "colorTemperature", value: intvalue)

    sendCommand("colorTem", intvalue)
    setCTColorName(intvalue)

    if(level != null) {
        setLevel(level, dur)
    }
}

def setCTColorName(value)
{
	if (value < 2600) {
		sendEvent(name: "colorName", value: "Warm White")
	}
	else if (value < 3500) {
		sendEvent(name: "colorName", value: "Incandescent")
	}
	else if (value < 4500) {
		sendEvent(name: "colorName", value: "White")
	}
	else if (value < 5500) {
		sendEvent(name: "colorName", value: "Daylight")
	}
	else if (value >=  5500) {
		sendEvent(name: "colorName", value: "Cool White")
	}
}
    
def setColor(value) {
	Utils.toLogger("info", "HSBColor = ${value}")
	if (value instanceof Map) {
		def h = value.containsKey("hue") ? value.hue : null
		def s = value.containsKey("saturation") ? value.saturation : null
		def b = value.containsKey("level") ? value.level : null
		setHsb(h, s, b)
	} else {
		Utils.toLogger("warn", "Invalid argument for setColor: ${value}")
    }
}

def setHsb(h,s,b)
{
	def hsbcmd = [h,s,b]
//	Utils.toLogger("debug", "Cmd = ${hsbcmd}")

	sendEvent(name: "hue", value: "${h}")
	sendEvent(name: "saturation", value: "${s}")
	if(b != device.currentValue("level")?.toInteger()) {
		sendEvent(name: "level", value: "${b}")
		setLevel(b)
	}
	
    def rgb = hubitat.helper.ColorUtils.hsvToRGB(hsbcmd)
	def rgbmap = [:]
	rgbmap.r = rgb[0]
	rgbmap.g = rgb[1]
	rgbmap.b = rgb[2]   
    
	sendEvent(name: "switch", value: "on")
	sendEvent(name: "colorMode", value: "RGB")
	sendCommand("color", rgbmap)    
}

def setHue(h)
{
    setHsb(h,device.currentValue( "saturation" )?:100,device.currentValue("level")?:100)
}

def setSaturation(s)
{
	setHsb(device.currentValue("hue")?:0,s,device.currentValue("level")?:100)
}

def setLevel(v, duration){
    setLevel(v)
}

def setLevel(v)
{
    if(v > 0) {
        sendEvent(name: "switch", value: "on")
        sendEvent(name: "level", value: v)
    } else {
        sendEvent(name: "switch", value: "off")		
    }

    if(aRngBright) {
        v = incBrightnessRange(v)
    }

    //Utils.toLogger("debug", "Sent Brightness = ${v}")
    sendCommand("brightness", v)
}

//Turn Hubitat's 0-100 Brightness range to the 0-254 expected by some devices
def incBrightnessRange(v)
{
	def newV = v * (254/100)
	return Math.round(newV)
}

//Go from 0-254 brightness range from some devices to Hubitat's 0-100 Brightness range. Maybe not needed?
def decBrightnessRange(v)
{
	def newV = v * (100/254)
	return Math.round(newV)
}

def white() {
}

def getDeviceSupport() {    
    def macAddr = parent.getMacAddr(device)
    def model = parent.getModel(device)
    Utils.toLogger("info", "getDeviceSupport() - macAddr: ${macAddr} - model: ${model}")
    parent.getGoveeAPI().getDeviceSupport(macAddr, model) { resp ->
        if (resp) {
            state.hasRetrievable = resp.retrievable
        }
    }
}

def sendCommand(String command, payload) {
    def macAddr = parent.getMacAddr(device)
    def model = parent.getModel(device)
    Utils.toLogger("info", "sendCommand() - macAddr: ${macAddr} - model: ${model}")
    
    parent.getGoveeAPI().sendCommand(macAddr, model, command, payload) { resp ->
        if (resp) {
            Utils.toLogger("debug", "resp.data = ${resp.data}")
        }
    }
}

def getDeviceState(){
    def macAddr = parent.getMacAddr(device)
    def model = parent.getModel(device)
	Utils.toLogger("info", "getDeviceState() - macAddr: ${macAddr} - model: ${model}")
    parent.getGoveeAPI().getDeviceState(macAddr, model) { resp ->
        if (resp) {
            def varPower = resp.find({it.powerState})?.powerState
            def varBrightness = resp.find({it.brightness})?.brightness
            def mapColor = resp.find({it.color})?.color                
            def varCT = resp.find({it.colorTemInKelvin})?.colorTemInKelvin

            //if(aRngBright){varBrightness=decBrightnessRange(varBrightness)}
            //Utils.toLogger("debug", "Recvd Brightness = ${varBrightness}")

            sendEvent(name: "switch", value: varPower)

            if(varBrightness) {
                sendEvent(name: "level", value: varBrightness)
            }

            if(varCT) {
                sendEvent(name: "colorTemperature", value: varCT)
                sendEvent(name: "colorMode", value: "CT")
                setCTColorName(varCT)					
            }

            if(mapColor) {
                def r = mapColor.r
                def g = mapColor.g
                def b = mapColor.b
                def HSVlst = hubitat.helper.ColorUtils.rgbToHSV([r,g,b])
                def hue = HSVlst[0].toInteger()
                def sat = HSVlst[1].toInteger()
                sendEvent(name: "hue", value: hue)
                sendEvent(name: "saturation", value: sat)
                sendEvent(name: "colorMode", value: "RGB")
            }
        }
    }
}

def poll() {
	refresh()
}

def refresh() {
    if(state.hasRetrievable) {
        getDeviceState()
    }
}

def updated() {
    setupDevice()
}

def setupDevice() {
    def macAddr = parent.getMacAddr(device)
    def model = parent.getModel(device)
    Utils.toLogger("info", "setupDevice() - macAddr: ${macAddr} - model: ${model}")
	getDeviceSupport()
	refresh()
}

def installed(){
    sendEvent(name: "hue", value: 0)
    sendEvent(name: "saturation", value: 100)
    sendEvent(name: "level", value: 100)
    setupDevice()
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
                log."${level}" "${msg}";
            }
        }
    }
    
    return instance;
}
