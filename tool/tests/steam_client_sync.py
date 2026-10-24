import hashlib, os, pathlib, subprocess, sys, tempfile, zipfile
INSTALLER = sys.argv[1]
with tempfile.TemporaryDirectory(prefix="dextop-sync-test-") as tmp:
    base=pathlib.Path(tmp); home=base/"home"; cdn=base/"cdn"; tools=base/"bin"
    home.mkdir(); cdn.mkdir(); tools.mkdir()
    curl=tools/"curl"
    curl.write_text("#!/usr/bin/python3\nimport os,sys,shutil\na=sys.argv[1:]; url=next(x for x in a if x.startswith('https://')); shutil.copyfile(os.path.join(os.environ['TEST_CDN'],url.rsplit('/',1)[1]),a[a.index('-o')+1])\n")
    curl.chmod(0o755)
    env=dict(os.environ,HOME=str(home),XDG_DATA_HOME=str(home/"data"),PATH=str(tools)+":"+os.environ["PATH"],TEST_CDN=str(cdn))
    root=home/"data/Steam"; manifest="steam_client_steamdeck_publicbeta_linuxarm64"
    def release(version,content,bad=False):
        part=cdn/"client.zip"
        with zipfile.ZipFile(part,"w") as z: z.writestr("steamrtarm64/steam",content)
        digest="0"*64 if bad else hashlib.sha256(part.read_bytes()).hexdigest()
        (cdn/manifest).write_text('"version" "'+str(version)+'"\n\t"client_linuxarm64_linuxarm64"\n{\n"file" "client.zip"\n"sha2" "'+digest+'"\n}\n')
    def run(*args,ok=True):
        result=subprocess.run(["bash",INSTALLER,*args],env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        assert (result.returncode==0)==ok,result.stdout
    release(100,"client-v1"); run()
    assert (root/"steamrtarm64/steam").read_text()=="client-v1"
    assert os.access(root/"steamrtarm64/steam",os.X_OK)
    (root/"package/beta").write_text("steamdeck_stable\n")
    run()
    assert (root/"package/beta").read_text().strip()=="steamdeck_publicbeta"
    (root/"package/beta").write_text("steamdeck_stable\n")
    run("--sync")
    assert (root/"package/beta").read_text().strip()=="steamdeck_publicbeta"
    assert (root/"package/droiddeck-installed").read_text().strip()=="100"
    release(101,"client-v2")
    # Steam may have downloaded the next manifest without installing the ARM64 client.
    (root/"package"/(manifest+".manifest")).write_bytes((cdn/manifest).read_bytes())
    run("--sync")
    assert (root/"steamrtarm64/steam").read_text()=="client-v2"
    assert (root/"package/droiddeck-installed").read_text().strip()=="101"
    release(102,"corrupt",bad=True); run("--sync",ok=False)
    assert (root/"steamrtarm64/steam").read_text()=="client-v2"
    assert (root/"package/beta").read_text().strip()=="steamdeck_publicbeta"
    assert '"101"' in (root/"package"/(manifest+".manifest")).read_text()
    print("PASS fresh install, same-version sync, version update, failed checksum preserves installed client and manifest")
