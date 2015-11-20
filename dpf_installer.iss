; Script generated by the Inno Setup Script Wizard.
; SEE THE DOCUMENTATION FOR DETAILS ON CREATING INNO SETUP SCRIPT FILES!

[Setup]
; NOTE: The value of AppId uniquely identifies this application.
; Do not use the same AppId value in installers for other applications.
; (To generate a new GUID, click Tools | Generate GUID inside the IDE.)
AppId={{0D5B5F16-9992-49DC-AD65-D66FE249CE67}
AppName=DPF Manager
AppVersion=1.2.2
AppVerName=DPF Manager 1.2.2
AppPublisher=DPF Manager
AppComments=DPF Manager
AppCopyright=Copyright (C) 2015
AppPublisherURL=http://dpfmanager.org/
AppSupportURL=http://dpfmanager.org/
AppUpdatesURL=http://dpfmanager.org/
;DefaultDirName={localappdata}\DPF Manager
UsePreviousAppDir=no
DefaultDirName={pf}\DPF Manager
DefaultGroupName=DPF Manager
OutputBaseFilename=DPF Manager-1.2.2
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
SetupIconFile=src\main\deploy\windows\DPF Manager.ico
UninstallDisplayIcon={app}\DPF Manager.ico
UninstallDisplayName=DPF Manager
ArchitecturesInstallIn64BitMode=x64
OutputDir=easyinnova

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "target\jfx\app\DPF Manager.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\jfx\app\DPF Manager-jfx.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "target\jfx\app\lib\*"; DestDir: "{app}\lib"; Flags: ignoreversion
Source: "additionalResources\*"; DestDir: "{app}"; Flags: ignoreversion
; Source: "additionalResources\*.dpf"; DestDir: "{localappdata}\DPF Manager"; Flags: ignoreversion
; NOTE: Don't use "Flags: ignoreversion" on any shared system files

[Icons]
Name: "{group}\DPFManager"; Filename: "{app}\DPF Manager.exe"
Name: "{commondesktop}\DPFManager"; Filename: "{app}\DPF Manager.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\DPF Manager.exe"; Description: "{cm:LaunchProgram,DPFManager}"; Flags: nowait postinstall skipifsilent

