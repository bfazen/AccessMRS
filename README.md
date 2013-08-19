AccessMRS is an Android client for accessing and editing OpenMRS patient data and forms.  This application is one part of a free and open-source application suite (including AccessForms, AccessAdmin, and AccessMaps) that was designed to provide Community Health Workers a Clinical Decision Support tool to help collect patient data and disseminate information, education and communication (IEC) materials.  AccessMRS is currently being piloted amongst Kenya MOH Community Health Volunteers in western Kenya.

# General
This application works as an interface for remotely viewing clinical data from OpenMRS, adding new clients to OpenMRS, providing patient-specific SmartForm reminders for individual patients, filling forms on a client, and sending data to OpenMRS through an encrypted SSL tunnel.

# Security
The software uses 128-bit encryption for all downloaded patient data.While the encryption provides some level of device security, security of patient data in AccessMRS can be greatly enhanced by installing the companion application AccessAdmin, which allows for remote device administration (including wiping patient data) through SMS. 

# Requirements
The AccessMRS app requires AccessForms to be installed on the phone in order to run.  AccessForms is a fork of ODK Collect software that is customized for better integration with AccessMRS.  On the OpenMRS server, AccessMRS interfaces with both the ODK Connector module and the XformsHelper module.  The following modules must therefore be installed in OpenMRS:
Serialization
Reporting
HtmlWidgets
Xforms
ODKConnector
XformsHelper

# Compile with Keystores
I compile our trustore/keystore into the apk for easy implementation, so I ignore it from this git repo. However, a default mytrustore.bks and mykeystore.bks are required to be in res/raw to compile, even if only they are just two blank files called res/raw/mytruststore.bks and res/raw/mykeystore.bks. AccessMRS will only attempt to use these default keystores if there is no keystore or truststore that can be imported from the sdcard during the first run.

# Installation
In order to help with device administration, this
