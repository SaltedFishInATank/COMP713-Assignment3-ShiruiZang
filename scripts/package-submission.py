"""Create a reviewable source ZIP without caches, runtimes or business database files."""
from pathlib import Path
import hashlib,json,shutil,zipfile
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT.parent
NAME='COMP713_Assessment3_Codebase_ShiruiZang_21136179.zip'
EXCLUDE={'.git','.gradle','.idea','target','build','dist','data','__pycache__'}
def include(p):
 rel=p.relative_to(ROOT)
 return not any(x in EXCLUDE for x in rel.parts) and p.name!='local.properties' and p.suffix.lower() not in {'.apk','.war'} and not p.name.endswith(('.mv.db','.trace.db')) and not (rel.parts[0]=='reports' and p.suffix=='.pdf')
files=sorted(p for p in ROOT.rglob('*') if p.is_file() and include(p))
archive=DEST/NAME
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
 for p in files:z.write(p,Path('Assessment3')/p.relative_to(ROOT))
with zipfile.ZipFile(archive) as z:
 bad=z.testzip()
 if bad:raise RuntimeError(bad)
 names=z.namelist()
 required=['backend/pom.xml','backend/src/main/webapp/index.html','android/gradlew.bat','android/gradle/wrapper/gradle-wrapper.jar','android/app/src/main/AndroidManifest.xml','consumer/pom.xml','README.md','docs/payara-setup.md','docs/payara-deployment.md','docs/payara-api-testing.md','docs/payara-troubleshooting.md']
 for name in required:
  if 'Assessment3/'+name not in names:raise RuntimeError('Missing '+name)
 for name in names:
  if any(part in EXCLUDE for part in Path(name).parts):raise RuntimeError('Generated folder '+name)
result={'archive':str(archive),'files':len(files),'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'integrity':'PASS','requiredFiles':'PASS','generatedFoldersExcluded':'PASS'}
for src,dst in [(ROOT/'backend/target/comp713-a3.war',DEST/'COMP713_Assessment3_ShiruiZang_21136179.war'),(ROOT/'android/app/build/outputs/apk/debug/app-debug.apk',DEST/'COMP713_Assessment3_Android_ShiruiZang_21136179.apk')]:
 if src.exists():shutil.copy2(src,dst)
runtimeApk=ROOT/'android/evidence/runtime-tested.apk'
if runtimeApk.exists():shutil.copy2(runtimeApk,DEST/'COMP713_Assessment3_Android_RuntimeTested_ShiruiZang_21136179.apk')
for p in (ROOT/'reports').glob('*.pdf'):shutil.copy2(p,DEST/p.name)
(DEST/'COMP713_Assessment3_Package_Checks.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
