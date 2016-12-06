/**
 *  Danfoss Living Connect Radiator Thermostat LC-13
 *
 *  Copyright 2016 Tom Philip
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Danfoss Living Connect Radiator Thermostat LC-13 v3", namespace: "tommysqueak", author: "Tom Philip") {
		capability "Thermostat Heating Setpoint"
		capability "Battery"
		capability "Configuration"
		capability "Switch"

		attribute "nextHeatingSetpoint", "number"

		// raw fingerprint zw:S type:0804 mfr:0002 prod:0005 model:0004 ver:1.01 zwv:3.67 lib:06 cc:80,46,81,72,8F,75,43,86,84 ccOut:46,81,8F
		//	0x0804 = device id, the inClusters are the commands
		fingerprint deviceId: "0x0804"
		fingerprint inClusters: "0x43, 0x46, 0x72, 0x75, 0x80, 0x81, 0x84, 0x86, 0x8F"
		// 0x80 = Battery v1
		// 0x46 = Climate Control Schedule v1
		// 0x81 = Clock v1
		// 0x72 = Manufacturer Specific v1
		// 0x8F = Multi Cmd v1 (Multi Command Encapsulated)
		// 0x75 = Protection v2
		// 0x43 = Thermostat Setpoint v2
		// 0x86 = Version v1
		// 0x84 = Wake Up v2
	}

	simulator {

	}

	// http://scripts.3dgo.net/smartthings/icons/
	//	TODO: create temp set like Nest thermostat - http://docs.smartthings.com/en/latest/device-type-developers-guide/tiles-metadata.html
	tiles(scale: 2) {
		multiAttributeTile(name:"richtemp", type:"lighting", width:6, height:4) {
			tileAttribute("device.heatingSetpoint", key: "PRIMARY_CONTROL") {
				attributeState("heat", label:'${currentValue}°', icon: "st.Weather.weather2",
					backgroundColors:[
						[value: 4, color: "#153591"],
						[value: 7, color: "#1e9cbb"],
						[value: 10, color: "#90d2a7"],
						[value: 13, color: "#44b621"],
						[value: 16, color: "#f1d801"],
						[value: 19, color: "#d04e00"],
						[value: 22, color: "#bc2323"]
						]
				)
			}
			tileAttribute ("device.nextHeatingSetpoint", key: "SECONDARY_CONTROL") {
				attributeState "heat", label:'Next ${currentValue}°'
			}
			tileAttribute("device.nextHeatingSetpoint", key: "SLIDER_CONTROL") {
				attributeState "default", action:"setHeatingSetpoint", unit:""
			}
		}
		standardTile("switcher", "device.switch", height: 2, width: 3, inactiveLabel: true, decoration: "flat") {
			state("off", action:"on", icon: "st.thermostat.heat", backgroundColor:"#153591")
			state("on", action:"off", icon: "st.thermostat.cool", backgroundColor:"#bc2323")
		}
		valueTile("battery", "device.battery", inactiveLabel: false, height: 2, width: 3, decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}
		controlTile("heatSliderControl", "device.nextHeatingSetpoint", "slider", height: 2, width: 6, inactiveLabel: false, range:"(4..22)") {
			state "setHeatingSetpoint", action:"setHeatingSetpoint"
		}

		main "richtemp"
		details(["richtemp", "heatSliderControl", "switcher", "battery"])
	}
}

//	Event order
//	Scenario - temp is changed via the app
//	* BatteryReport (seem to get one of these everytime it wakes up! This is ood, as we don't ask for it)
//  * WakeUpNotification (the new temperature, set by the app is sent)
//  * ScheduleOverrideReport (we don't handle it)
//  * ThermostatSetpointReport (we receivce the new temp we sent when it woke up. We also set the heatingSetpoint and next one, all is aligned :))

//	Scenario - temp is changed via the app - alternative (this sometimes happens instead)
//	* ThermostatSetpointReport - resets the next temp back to the current temp. Not what we want :(
//	* BatteryReport
//	* WakeUpNotification
//	* ScheduleOverrideReport

//	Scenario - temp is changed on the radiator thermostat
//	* BatteryReport (seem to get one of these everytime it wakes up! This is ood, as we don't ask for it)
//  * ThermostatSetpointReport (we receive the temp set on the thermostat. We also set the heatingSetpoint and next one. If we set a next one, it's overwritten.)
//  * ScheduleOverrideReport (we don't handle it)
//  * WakeUpNotification (no temp is sent, as they're both the same coz of ThermostatSetpointReport)

//	Scenario - wakes up
//	* BatteryReport
//	* WakeUpNotification

//	WakeUpINterval 1800 (5mins)

//	defaultWakeUpIntervalSeconds: 300, maximumWakeUpIntervalSeconds: 1800 (30 mins),
//	minimumWakeUpIntervalSeconds: 60, wakeUpIntervalStepSeconds: 60

// All messages from the device are passed to the parse method.
// It is responsible for turning those messages into something the SmartThings platform can understand.
def parse(String description) {
	log.debug "Parsing '${description}'"

	def result = null
	//	The commands in the array are to map to versions of the command class. eg physicalgraph.zwave.commands.wakeupv1 vs physicalgraph.zwave.commands.wakeupv2
	//	If none specified, it'll use the latest version of that command class.
	def cmd = zwave.parse(description)
	if (cmd) {
		result = zwaveEvent(cmd)
		log.debug "Parsed ${cmd} to ${result.inspect()}"
	}
	else {
		log.debug "Non-parsed event: ${description}"
	}
	result
}

//	catch all unhandled events
def zwaveEvent(physicalgraph.zwave.Command cmd) {
	return createEvent(descriptionText: "Uncaptured event for ${device.displayName}: ${cmd}")
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd) {
	//	Parsed ThermostatSetpointReport(precision: 2, reserved01: 0, scale: 0, scaledValue: 21.00, setpointType: 1, size: 2, value: [8, 52])

	// So we can respond with same format later, see setHeatingSetpoint()
	state.scale = cmd.scale
	state.precision = cmd.precision

	def eventList = []
	def cmdScale = cmd.scale == 1 ? "F" : "C"
	def radiatorTemperature = Double.parseDouble(convertTemperatureIfNeeded(cmd.scaledValue, cmdScale, cmd.precision))

	def currentTemperature = currentDouble("heatingSetpoint")
	def nextTemperature = currentDouble("nextHeatingSetpoint")

	log.debug "SetpointReport. current:${currentTemperature} next:${nextTemperature} radiator:${radiatorTemperature}"

	def deviceTempMap = [name: "heatingSetpoint"]
	deviceTempMap.value = radiatorTemperature
	deviceTempMap.unit = getTemperatureScale()

	def offOrOn = [name: "switch", displayed: false]
	if(radiatorTemperature > 4) {
		offOrOn.value = "on"
	}
	else {
		offOrOn.value = "off"
	}

	eventList << createEvent(deviceTempMap)
	eventList << createEvent(offOrOn)

	if(nextTemperature == 0) {
		//	initialise the nextHeatingSetpoint, on the very first time we install and get the devices temp
		eventList << createEvent(name:"nextHeatingSetpoint", value: radiatorTemperature, unit: getTemperatureScale())
	}

	eventList
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
	log.debug "Wakey wakey"

  def event = createEvent(descriptionText: "${device.displayName} woke up", displayed: false)
  def cmds = []

	// Only ask for battery if we haven't had a BatteryReport in a while
	if (!state.lastBatteryReportReceivedAt || (new Date().time) - state.lastBatteryReportReceivedAt > daysToTime(7)) {
		log.debug "Asking for battery report"
		cmds << zwave.batteryV1.batteryGet().format()
	}

  //	Send the new temperature, if we haven't yet sent it
  log.debug "New temperature check. Next: ${device.currentValue("nextHeatingSetpoint")} vs Current: ${device.currentValue("heatingSetpoint")}"

  if (currentDouble("nextHeatingSetpoint") != 0) {
		log.debug "Sending new temperature ${device.currentValue("nextHeatingSetpoint")}"
		cmds << setHeatingSetpointCommand(device.currentValue("nextHeatingSetpoint")).format()
		//	Mop up any flwas, ask for the devices temp
		//cmds << zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format()
		//	Be sure to set the new temp ourselves, as commands don't always run in order
		//event = createEvent(name: "heatingSetpoint", value: device.currentValue("nextHeatingSetpoint").doubleValue(), unit: getTemperatureScale())
	}

  cmds << zwave.wakeUpV1.wakeUpNoMoreInformation().format()
  delayBetween(cmds, 1000)
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {  // Special value for low battery alert
		map.value = 1
		map.descriptionText = "Low Battery"
	} else {
		map.value = cmd.batteryLevel
	}
	// Store time of last battery update so we don't ask every wakeup, see WakeUpNotification handler
	state.lastBatteryReportReceivedAt = new Date().time
	createEvent(map)
}

//
//	commands (that it can handle, must implement those that match it's capabilities, so SmartApps can call these methods)
//
//	Thermostat Heating Setpoint
def setHeatingSetpoint(degrees) {
	setHeatingSetpoint(degrees.toDouble())
}

def setHeatingSetpoint(Double degrees) {
	log.debug "Storing temperature for next wake ${degrees}"
	sendEvent(name:"nextHeatingSetpoint", value: degrees, unit: getTemperatureScale())

	if(degrees > 4) {
		sendEvent(name:"switch", value: "on", displayed: false)
	}
	else {
		sendEvent(name:"switch", value: "off", displayed: false)
	}
}

def setHeatingSetpointCommand(Double degrees) {
	log.trace "setHeatingSetpoint ${degrees}"
	def deviceScale = state.scale ?: 0
	def deviceScaleString = deviceScale == 1 ? "F" : "C"
	def locationScale = getTemperatureScale()
	def precision = state.precision ?: 2

	def convertedDegrees
	if (locationScale == "C" && deviceScaleString == "F") {
		convertedDegrees = celsiusToFahrenheit(degrees)
	} else if (locationScale == "F" && deviceScaleString == "C") {
		convertedDegrees = fahrenheitToCelsius(degrees)
	} else {
		convertedDegrees = degrees
	}

	zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 1, scale: deviceScale, precision: precision, scaledValue: convertedDegrees)
}

def on() {
	setHeatingSetpoint(20)
}

def off() {
	setHeatingSetpoint(4)
}

def installed() {
	log.debug "installed"
	configure()
}

def updated() {
	log.debug("updated")
	configure()
}

def configure() {
	log.debug("configure")
	delayBetween([
  	//	Not sure if this is needed :/
    zwave.configurationV1.configurationSet(parameterNumber:1, size:2, scaledConfigurationValue:100).format(),
    //	Get it's configured info, like it's scale (Celsius, Farenheit) Precicsion
  	// 1 = SETPOINT_TYPE_HEATING_1
  	zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format(),
    zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]).format(),
  	//	Set it's time/clock. Do we need to do this periodically, like the battery check?
  	currentTimeCommand(),
		// Make sure sleepy battery-powered sensors send their
    // WakeUpNotifications to the hub every 5 mins
    zwave.wakeUpV1.wakeUpIntervalSet(seconds:300, nodeid:zwaveHubNodeId).format()
	], 1000)
}

private setClock() {
	// set the clock once a week
	def now = new Date().time
	if (!state.lastClockSet || now - state.lastClockSet > daysToTime(7)) {
		state.lastClockSet = now
		currentTimeCommand()
	}
	else {
		null
	}
}

private daysToTime(days) {
	days*24*60*60*1000
}

private currentTimeCommand() {
    def nowCalendar = Calendar.getInstance(location.timeZone)
    log.debug "Setting clock to ${nowCalendar.getTime().format("dd-MM-yyyy HH:mm z", location.timeZone)}"
    def weekday = nowCalendar.get(Calendar.DAY_OF_WEEK) - 1
    if (weekday == 0) {
        weekday = 7
    }
    zwave.clockV1.clockSet(hour: nowCalendar.get(Calendar.HOUR_OF_DAY), minute: nowCalendar.get(Calendar.MINUTE), weekday: weekday).format()
}

private currentDouble(attributeName) {
	if(device.currentValue(attributeName)) {
		return device.currentValue(attributeName).doubleValue()
	}
	else {
		return 0d
	}
}
