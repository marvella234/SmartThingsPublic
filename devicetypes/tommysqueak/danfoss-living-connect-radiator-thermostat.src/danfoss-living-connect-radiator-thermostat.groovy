/**
 *  Danfoss Living Connect Radiator Thermostat LC-13
 *
 *  Copyright 2015 Tom Philip
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
	definition (name: "Danfoss Living Connect Radiator Thermostat LC-13 v1", namespace: "tommysqueak", author: "Tom Philip") {
		capability "Thermostat Heating Setpoint"
        capability "Battery"
        capability "Configuration"

        attribute "nextHeatingSetpoint", "number"

		//	TODO: why fingerpint is not working? z-wave thermostat's fingerprint is below, which is the one it picks up.
    	//	fingerprint deviceId: "0x08"
		//	fingerprint inClusters: "0x43,0x40,0x44,0x31"
		// raw fingerprint 0 0 0x0804 0 0 0 d 0x80 0x46 0x81 0x72 0x8F 0x75 0x43 0x86 0x84 0xEF 0x46 0x81 0x8F
        //	0x0804 = device id, the inClusters are the commands
        fingerprint deviceId: "0x0804"
        fingerprint inClusters: "0x43, 0x46, 0x72, 0x75, 0x80, 0x81, 0x84, 0x86, 0x8F, 0xEF"
        // 0x80 = Battery v1
        // 0x46 = Climate Control Schedule v1
        // 0x81 = Clock v1
        // 0x72 = Manufacturer Specific v1
        // 0x8F = Multi Cmd v1 (Multi Command Encapsulated)
        // 0x75 = Protection v2
        // 0x43 = Thermostat Setpoint v2
        // 0x86 = Version v1
        // 0x84 = Wake Up v2
        // 0xEF = Mark
	}

	simulator {
        // These show up in the IDE simulator "messages" drop-down to test
        // sending event messages to your device handler
    	status "low battery alert":
       		zwave.batteryV1.batteryReport(batteryLevel:0xFF).incomingMessage()
	}

	// http://scripts.3dgo.net/smartthings/icons/
	//	TODO: create temp set like Nest thermostat - http://docs.smartthings.com/en/latest/device-type-developers-guide/tiles-metadata.html
	tiles(scale: 2) {
        multiAttributeTile(name:"richtemp", type:"thermostat", width:6, height:4) {
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
           		attributeState "heat", label:'${currentValue}°'
            }
        }
        valueTile("battery", "device.battery", inactiveLabel: false, height: 2, width: 3, decoration: "flat") {
            state "battery", label:'${currentValue}% battery', unit:""
        }
        controlTile("heatSliderControl", "device.nextHeatingSetpoint", "slider", height: 2, width: 6, inactiveLabel: false, range:"(4..22)") {
            state "setHeatingSetpoint", action:"setHeatingSetpoint", backgroundColor:"#d04e00"
        }
        valueTile("nextHeatingSetpointValue", "device.nextHeatingSetpoint", inactiveLabel: false, height: 2, width: 3, decoration: "flat") {
            state("heat", label: 'Next ${currentValue}°', unit: "",
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

        main "richtemp"
        details(["richtemp", "heatSliderControl", "nextHeatingSetpointValue", "battery"])
	}
}

//	'Get' commands are sent to the device, the device typically responds with a 'Report' command.
//	'Set' commands set some value on the device
//	Eg https://graph.api.smartthings.com/ide/doc/zwave-utils.html#clockV1
//	Sending ClockGet to the device, the device will send a command/event ClockReport
//	Sending ClockSet set the clock on the device

//	Event order
//	Scenario - temp is changed via the app
//	* BatteryReport (seem to get one of these everytime it wakes up! This is ood, as we don't ask for it)
//  * WakeUpNotification (the new temperature, set by the app is sent)
//  * ScheduleOverrideReport (we don't handle it)
//  * ThermostatSetpointReport (we receivce the new temp we sent when it woke up. We also set the heatingSetpoint and next one, all is aligned :))

//	Scenario - temp is changed vis the app - alternative (this sometimes happends instead)
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
    } else {
		log.debug "Non-parsed event: ${description}"
    }
    result

}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    // This will capture any commands not handled by other instances of zwaveEvent
    // and is recommended for development so you can see every command the device sends
    return createEvent(descriptionText: "Unlistened to event. ${device.displayName}: ${cmd}")
}

// http://dz.prosyst.com/pdoc/mBS_SH_SDK_7.3.0/modules/zwave/api/com/prosyst/mbs/services/zwave/commandclasses/CCClimateControlSchedule.html
def zwaveEvent1(physicalgraph.zwave.commands.climatecontrolschedulev1.ScheduleOverrideReport cmd)
{
	//ScheduleOverrideReport(overrideState: 127, overrideType: 0, reserved01: 0)
}

Short ENERGY_SAVE_MODE = 122
Short FROST_PROTECTION = 121
Short UNUSED = 127



// http://dz.prosyst.com/pdoc/mBS_SH_SDK_7.3.0/modules/zwave/api/com/prosyst/mbs/services/zwave/commandclasses/CCClimateControlSchedule.html
def zwaveEvent(physicalgraph.zwave.commands.climatecontrolschedulev1.ScheduleReport cmd)
{
	def DAYS_OF_WEEK = ["Invalid", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
	//	127 = unused
	log.debug "Schedule for ${DAYS_OF_WEEK[cmd.weekday]}"
    logSwitchpoint(cmd.switchpoint0)
    logSwitchpoint(cmd.switchpoint1)
    logSwitchpoint(cmd.switchpoint2)
    logSwitchpoint(cmd.switchpoint3)
    logSwitchpoint(cmd.switchpoint4)
    logSwitchpoint(cmd.switchpoint5)
    logSwitchpoint(cmd.switchpoint6)
    logSwitchpoint(cmd.switchpoint7)
    logSwitchpoint(cmd.switchpoint8)
    //def hour = 1
    //def minute = 2
    //def temp = 127

    //Integer switchpoint = hour + (minute << 8) + (temp << 16);
    //Integer switchpointAlternative = 0x01027F  // same as above 1 hr 2 min and 127(not used)
}

private logSwitchpoint(Integer switchpoint)
{
   	def hour = switchpoint & 0xFF0000
   	def minute = switchpoint & 0x00FF00
	def temperature = switchpoint & 0x0000FF

	log.debug "At ${hour}:${minute} the temperature will be ${temperature}. Raw: ${switchpoint}"
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd)
{
	//	Parsed ThermostatSetpointReport(precision: 2, reserved01: 0, scale: 0, scaledValue: 21.00, setpointType: 1, size: 2, value: [8, 52])

	def cmdScale = cmd.scale == 1 ? "F" : "C"
	def radiatorTemperature = Double.parseDouble(convertTemperatureIfNeeded(cmd.scaledValue, cmdScale, cmd.precision))

	def currentTemperature = currentDouble("heatingSetpoint")
    def nextTemperature = currentDouble("nextHeatingSetpoint")

	log.debug "SetpointReport. current:${currentTemperature} next:${nextTemperature} radiator:${radiatorTemperature}"

	//	If we've just set (ie sent the temp), then don't override it with the radiator temp,
    //	even though we might want the rad to override, we can't it's already been sent!
	def now = new Date().time
    if(device.currentState("heatingSetpoint") && currentTemperature != radiatorTemperature && device.currentState("heatingSetpoint").date.time < (now - 1000))
    {
    	log.debug "setting current and next to the radiators temperature"
        currentTemperature = radiatorTemperature
        nextTemperature = radiatorTemperature
    }

	def deviceTempMap = [name: "heatingSetpoint"]
	deviceTempMap.value = currentTemperature
	deviceTempMap.unit = getTemperatureScale()

	def chosenTempMap = [name: "nextHeatingSetpoint"]
	chosenTempMap.value = nextTemperature
	chosenTempMap.unit = getTemperatureScale()
    chosenTempMap.displayed = false

	// So we can respond with same format later, see setHeatingSetpoint()
	state.scale = cmd.scale
	state.precision = cmd.precision
	[createEvent(deviceTempMap), createEvent(chosenTempMap)]
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
	log.debug "Wakey wakey"
    //	TODO: check commands are being sent as a multi-command (encap)

    def schedule = new physicalgraph.zwave.commands.climatecontrolschedulev1.ScheduleGet()
    schedule.weekday = 4
    //schedule.overrideState = 1
    //schedule.overrideType = -2

    def wakeUpCap = new physicalgraph.zwave.commands.wakeupv2.WakeUpIntervalCapabilitiesGet()

    def wakeUp = new physicalgraph.zwave.commands.wakeupv2.WakeUpIntervalGet()

    def clock = new physicalgraph.zwave.commands.clockv1.ClockGet()
    //clock.weekday = 1
    //clock.hour = 22
    //clock.minute = 18

    def event = createEvent(descriptionText: "${device.displayName} woke up", displayed: false)
    def cmds = []

	for (com in oneTimeConfigurationCommands())
    {
    	cmds << com
    	cmds << "delay 1500"
	}

    // Only ask for battery if we haven't had a BatteryReport in a while
    if (!state.lastbatt || (new Date().time) - state.lastbatt > daysToTime(7))
    {
    	log.debug "Asking for battery report"
    	cmds << zwave.batteryV1.batteryGet().format()
    	cmds << "delay 1200" // leave time for device to respond to batteryGet
    }

    //	Send the new temperature, if we haven't yet sent it
    log.debug "New temperature check. Next: ${device.currentValue("nextHeatingSetpoint")} vs Current: ${device.currentValue("heatingSetpoint")}"
    //	TODO: take into account nextHeatingSetpoint having no value
    if (currentDouble("nextHeatingSetpoint") != currentDouble("heatingSetpoint"))
    {
    	log.debug "Sending new temperature ${device.currentValue("nextHeatingSetpoint")}"
    	cmds << setHeatingSetpointCommand(device.currentValue("nextHeatingSetpoint")).format()
    	cmds << "delay 1500"
        //	Mop up any flwas, ask for the devices temp
        cmds << zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format()
    	cmds << "delay 1500"
		//	Be sure to set the new temp ourselves, as commands don't always run in order
		event = createEvent(name: "heatingSetpoint", value: device.currentValue("nextHeatingSetpoint").doubleValue(), unit: getTemperatureScale())
    }

    //cmds << wakeUpCap.format()
	//cmds << "delay 1500"

    //cmds << wakeUp.format()
    //cmds << "delay 1500"

    //cmds << schedule.format()
    //cmds << "delay 1500"

    cmds << zwave.wakeUpV1.wakeUpNoMoreInformation().format()
    [event, response(cmds)] // return a list containing the event and the result of response()
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
    def map = [ name: "battery", unit: "%" ]
    if (cmd.batteryLevel == 0xFF) {  // Special value for low battery alert
        map.value = 1
        map.descriptionText = "${device.displayName} has a low battery"
        map.isStateChange = true
    } else {
        map.value = cmd.batteryLevel
    }
    // Store time of last battery update so we don't ask every wakeup, see WakeUpNotification handler
    state.lastbatt = new Date().time
    createEvent(map)
}

//
//	commands (that it can handle, must implement those that match it's capabilities, so SmartApps can call these methods)
//
//	Thermostat Heating Setpoint
def setHeatingSetpoint(degrees)
{
    setHeatingSetpoint(degrees.toDouble())
}

def setHeatingSetpoint(Double degrees)
{
	log.debug "Storing temperature for next wake ${degrees}"
	//	TODO: should this be convertedDegrees?
    sendEvent(name:"nextHeatingSetpoint", value: degrees, unit: getTemperatureScale())
}

def setHeatingSetpointCommand(Double degrees)
{
	log.trace "setHeatingSetpoint ${degrees}"
	def deviceScale = state.scale ?: 1
	def deviceScaleString = deviceScale == 2 ? "C" : "F"
    def locationScale = getTemperatureScale()
	def p = (state.precision == null) ? 1 : state.precision

    def convertedDegrees
    if (locationScale == "C" && deviceScaleString == "F") {
    	convertedDegrees = celsiusToFahrenheit(degrees)
    } else if (locationScale == "F" && deviceScaleString == "C") {
    	convertedDegrees = fahrenheitToCelsius(degrees)
    } else {
    	convertedDegrees = degrees
    }
    //	1 = SETPOINT_TYPE_HEATING_1
	zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 1, scale: deviceScale, precision: p, scaledValue: convertedDegrees)
}

def setSchedule() {
	log.debug "Executing 'setSchedule'"
	// TODO: handle 'setSchedule' command
}

private setClock()
{	// set the clock once a week
	def now = new Date().time
    if (!state.lastClockSet || now - state.lastClockSet > daysToTime(7))
	{
		state.lastClockSet = now
        currentTimeCommand()
    }
    else
    {
    	null
    }
}

private daysToTime(days)
{
	days*24*60*60*1000
}

private currentTimeCommand()
{
    def nowCalendar = Calendar.getInstance(location.timeZone)
    log.debug "Setting clock to ${nowCalendar.getTime().format("dd-mm-yyyy HH:mm z", location.timeZone)}"
    def weekday = nowCalendar.get(Calendar.DAY_OF_WEEK) - 1
    if (weekday == 0)
    {
        weekday = 7
    }
    zwave.clockV1.clockSet(hour: nowCalendar.get(Calendar.HOUR_OF_DAY), minute: nowCalendar.get(Calendar.MINUTE), weekday: weekday).format()
}

private configurationCommands()
{
	[
    	//	Get it's configured info, like it's scale (Celsius, Farenheit) Precicsion, or do we just know it?
    	// 1 = SETPOINT_TYPE_HEATING_1
    	zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1).format(),

    	//	Set it's time/clock. Do we need to do this periodically, like the battery check?
    	currentTimeCommand(),

    	// Make sure sleepy battery-powered sensors send their
        // WakeUpNotifications to the hub every 5 mins
        zwave.wakeUpV1.wakeUpIntervalSet(seconds:300, nodeid:zwaveHubNodeId).format()
    ]
}

private currentDouble(attributeName)
{
	if(device.currentValue(attributeName))
    {
    	return device.currentValue(attributeName).doubleValue()
    }
    else
    {
    	return 0d
    }
}

private oneTimeConfigurationCommands()
{
	def commands = []
	//if (!state.oneTimeConfiguration)
	if (true)
    {
    	log.debug "setting one time configuration"
    	state.oneTimeConfiguration = true
        commands + configurationCommands()
    }

	return commands
}

// If you add the Configuration capability to your device type, this
// command will be called right after the device joins to set
// device-specific configuration commands.
def configure() {
	log.debug "Executing 'configure'"
    delayBetween(oneTimeConfigurationCommands(), 1500)
}
