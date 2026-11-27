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

function GetDriveType(lpRootPathName: string): LongWord; external 'GetDriveTypeW@kernel32.dll stdcall';
function GetLogicalDrives: LongWord; external 'GetLogicalDrives@kernel32.dll stdcall';

function GetModsDirectory(Param: string): string;
begin
  Result := AddBackslash(InstancePage.Values[0]) + 'mods';
end;

function GetPlayerHostDirectory(Param: string): string;
begin
  Result := AddBackslash(InstancePage.Values[0]) + 'config\skuacraft\player-host';
end;

function IsDirectorySearchExcluded(DirectoryName: string): Boolean;
var
  Name: string;
begin
  Name := Lowercase(DirectoryName);
  Result :=
    (Name = 'windows') or
    (Name = 'program files') or
    (Name = 'program files (x86)') or
    (Name = 'programdata') or
    (Name = '$recycle.bin') or
    (Name = 'system volume information') or
    (Name = 'recovery') or
    (Name = 'perflogs') or
    (Name = 'appdata') or
    (Name = 'temp') or
    (Name = '.git') or
    (Name = 'node_modules') or
    (Name = '.gradle') or
    (Name = '.nuget');
end;

// Finds a directory named "minecraft" with an "instances" child.
// The depth limit keeps fallback discovery bounded on machines with large drives.
function FindInstanceInsideInstancesDirectory(InstancesDirectory: string): string;
var
  FindRec: TFindRec;
  CandidateDirectory: string;
  SelectedDirectory: string;
  ExistingSkuaCraftDirectory: string;
  CandidateCount: Integer;
begin
  Result := '';
  CandidateCount := 0;
  SelectedDirectory := '';
  ExistingSkuaCraftDirectory := '';

  // Also support a path that is already an individual Minecraft instance.
  if DirExists(AddBackslash(InstancesDirectory) + 'mods') or
     DirExists(AddBackslash(InstancesDirectory) + 'config') then
  begin
    Result := InstancesDirectory;
    Exit;
  end;

  if not FindFirst(AddBackslash(InstancesDirectory) + '*', FindRec) then
    Exit;

  try
    repeat
      if (FindRec.Name <> '.') and (FindRec.Name <> '..') and
         ((FindRec.Attributes and $10) <> 0) then
      begin
        CandidateDirectory := AddBackslash(InstancesDirectory) + FindRec.Name;
        if DirExists(AddBackslash(CandidateDirectory) + 'mods') or
           DirExists(AddBackslash(CandidateDirectory) + 'config') then
        begin
          CandidateCount := CandidateCount + 1;
          SelectedDirectory := CandidateDirectory;

          // When several profiles exist, prefer the one already using SkuaCraft.
          if DirExists(AddBackslash(CandidateDirectory) + 'config\skuacraft') then
            ExistingSkuaCraftDirectory := CandidateDirectory;
        end;
      end;
    until not FindNext(FindRec);
  finally
    FindClose(FindRec);
  end;

  if ExistingSkuaCraftDirectory <> '' then
    Result := ExistingSkuaCraftDirectory
  else if CandidateCount = 1 then
    Result := SelectedDirectory;
end;

function FindMinecraftInstances(Directory: string; Depth: Integer): string;
var
  FindRec: TFindRec;
  ChildDirectory: string;
  CandidateDirectory: string;
begin
  Result := '';

  if not DirExists(Directory) then
    Exit;

  if Lowercase(ExtractFileName(RemoveBackslash(Directory))) = 'minecraft' then
  begin
    CandidateDirectory := AddBackslash(Directory) + 'instances';
    if DirExists(CandidateDirectory) then
    begin
      Result := FindInstanceInsideInstancesDirectory(CandidateDirectory);
      if Result <> '' then
        Exit;
    end;
  end;

  if Depth <= 0 then
    Exit;

  if FindFirst(AddBackslash(Directory) + '*', FindRec) then
  begin
    try
      repeat
        if (FindRec.Name <> '.') and (FindRec.Name <> '..') and
           ((FindRec.Attributes and $10) <> 0) and
           not IsDirectorySearchExcluded(FindRec.Name) then
        begin
          ChildDirectory := AddBackslash(Directory) + FindRec.Name;
          Result := FindMinecraftInstances(ChildDirectory, Depth - 1);
          if Result <> '' then
            Exit;
        end;
      until not FindNext(FindRec);
    finally
      FindClose(FindRec);
    end;
  end;
end;

function FindDefaultInstanceDirectory(): string;
var
  ProfileDirectory: string;
  CandidateDirectory: string;
  DriveMask: LongWord;
  DriveIndex: Integer;
  DriveRoot: string;
begin
  Result := '';
  ProfileDirectory := ExpandConstant('{userprofile}');

  // Prefer the standard CurseForge location.
  CandidateDirectory := AddBackslash(ProfileDirectory) + 'curseforge\minecraft\Instances';
  if DirExists(CandidateDirectory) then
  begin
    Result := FindInstanceInsideInstancesDirectory(CandidateDirectory);
    Exit;
  end;

  // Check common alternate CurseForge locations before searching local drives.
  CandidateDirectory := AddBackslash(ExpandConstant('{userdocs}')) + 'CurseForge\Minecraft\Instances';
  if DirExists(CandidateDirectory) then
  begin
    Result := FindInstanceInsideInstancesDirectory(CandidateDirectory);
    Exit;
  end;

  CandidateDirectory := AddBackslash(ExpandConstant('{userappdata}')) + 'CurseForge\Minecraft\Instances';
  if DirExists(CandidateDirectory) then
  begin
    Result := FindInstanceInsideInstancesDirectory(CandidateDirectory);
    Exit;
  end;

  CandidateDirectory := AddBackslash(ExpandConstant('{localappdata}')) + 'CurseForge\Minecraft\Instances';
  if DirExists(CandidateDirectory) then
  begin
    Result := FindInstanceInsideInstancesDirectory(CandidateDirectory);
    Exit;
  end;

  // Search this user's profile first, then other fixed local drives.
  Result := FindMinecraftInstances(ProfileDirectory, 8);
  if Result <> '' then
    Exit;

  DriveMask := GetLogicalDrives();
  for DriveIndex := 0 to 25 do
  begin
    if (DriveMask and (1 shl DriveIndex)) <> 0 then
    begin
      DriveRoot := Chr(Ord('A') + DriveIndex) + ':\';
      if GetDriveType(DriveRoot) = 3 then
      begin
        Result := FindMinecraftInstances(DriveRoot, 7);
        if Result <> '' then
          Exit;
      end;
    end;
  end;
end;

procedure InitializeWizard();
var
  DefaultInstanceDirectory: string;
begin
  InstancePage := CreateInputDirPage(
    wpWelcome,
    'Choose your Minecraft instance',
    'Select the Forge instance folder',
    'The installer looks for your CurseForge installation and preselects a Forge profile inside its Instances folder. If it finds multiple profiles, choose the correct profile folder. Otherwise, it searches for other Minecraft\Instances folders on your local drives. You can still browse to a different instance.' + #13#10 + #13#10 +
    'CurseForge: open the modpack profile menu and choose Open Folder.' + #13#10 +
    'Official Launcher: Installations > your Forge profile > Edit > More Options > Game Directory.',
    False,
    ''
  );
  InstancePage.Add('Minecraft instance folder:');
  DefaultInstanceDirectory := FindDefaultInstanceDirectory();
  InstancePage.Values[0] := DefaultInstanceDirectory;
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
