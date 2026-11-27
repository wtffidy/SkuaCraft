#define AppName "SkuaCraft"
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif

[Setup]
AppId={{A1F1F4B8-8B2D-4C2C-AF7E-5F1E4B3A9C21}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=wtffidy
DefaultDirName={userappdata}\SkuaCraftInstaller
DisableDirPage=yes
DisableProgramGroupPage=yes
Uninstallable=no
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
WizardStyle=modern
WizardResizable=no
CloseApplications=yes
RestartApplications=no
SetupLogging=yes
Compression=lzma2
SolidCompression=yes
OutputDir=..\release
OutputBaseFilename=SkuaCraft-Setup-{#AppVersion}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "..\release\SkuaCraft-{#AppVersion}.jar"; DestDir: "{code:GetModsDirectory}"; Flags: ignoreversion
Source: "..\release\Rinku-3.0.4.jar"; DestDir: "{code:GetModsDirectory}"; Flags: ignoreversion
Source: "..\player-host\publish\win-x64\*"; DestDir: "{code:GetPlayerHostDirectory}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
Type: files; Name: "{code:GetModsDirectory}\SkuaCraft-*.jar"
Type: files; Name: "{code:GetModsDirectory}\Rinku-*.jar"
Type: files; Name: "{code:GetModsDirectory}\rinku-*.jar"

[Code]
var
  InstancePage: TInputDirWizardPage;

function GetModsDirectory(Param: string): string;
begin
  Result := AddBackslash(InstancePage.Values[0]) + 'mods';
end;

function GetPlayerHostDirectory(Param: string): string;
begin
  Result := AddBackslash(InstancePage.Values[0]) + 'config\skuacraft\player-host';
end;

procedure InitializeWizard();
begin
  InstancePage := CreateInputDirPage(
    wpWelcome,
    'Choose your Minecraft instance',
    'Select the Forge instance folder',
    'Choose the folder that contains your Minecraft instance. The installer adds SkuaCraft and Rinku to its mods folder and installs PlayerHost under config\skuacraft. Existing SkuaCraft settings and login data are preserved.' + #13#10 + #13#10 +
    'CurseForge: open the modpack profile menu and choose Open Folder.' + #13#10 +
    'Official Launcher: Installations > your Forge profile > Edit > More Options > Game Directory.',
    False,
    ''
  );
  InstancePage.Add('Minecraft instance folder:');
  InstancePage.Values[0] := '';
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  InstanceDirectory: string;
begin
  Result := True;

  if CurPageID <> InstancePage.ID then
    Exit;

  InstanceDirectory := Trim(InstancePage.Values[0]);
  if InstanceDirectory = '' then
  begin
    MsgBox('Select your Minecraft instance folder before continuing.', mbError, MB_OK);
    Result := False;
    Exit;
  end;

  if not DirExists(InstanceDirectory) then
  begin
    MsgBox('The selected folder does not exist:' + #13#10 + InstanceDirectory, mbError, MB_OK);
    Result := False;
    Exit;
  end;

  if not DirExists(AddBackslash(InstanceDirectory) + 'mods') and
     not DirExists(AddBackslash(InstanceDirectory) + 'config') then
  begin
    MsgBox('That folder does not look like a Minecraft instance. Select the folder containing the mods and config folders, not the mods folder itself.', mbError, MB_OK);
    Result := False;
    Exit;
  end;

  if not ForceDirectories(AddBackslash(InstanceDirectory) + 'mods') then
  begin
    MsgBox('Could not create the instance mods folder. Check that you have permission to modify this instance.', mbError, MB_OK);
    Result := False;
    Exit;
  end;

  if not ForceDirectories(AddBackslash(InstanceDirectory) + 'config') then
  begin
    MsgBox('Could not create the instance config folder. Check that you have permission to modify this instance.', mbError, MB_OK);
    Result := False;
    Exit;
  end;
end;
