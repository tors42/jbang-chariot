# Download and Install JBang

https://www.jbang.dev/download/

# Run local

    $ jbang ./scripts/membercount.java
    Team Lichess Swiss has 212788 members!


# Run public (downloads and runs locally, from a public source)

    $ jbang membercount@tors42/jbang-chariot
    Team Lichess Swiss has 212788 members!

## That looks scary, yeah? Docker to the rescue!

    $ docker run --rm --interactive --tty --entrypoint /bin/bash jbangdev/jbang-action
    Unable to find image 'jbangdev/jbang-action:latest' locally
    latest: Pulling from jbangdev/jbang-action
    f3ef4ff62e0d: Pull complete
    706b9b9c1c44: Pull complete
    562d89eb239f: Pull complete
    c7788248e4a0: Pull complete
    6ef097673c9f: Pull complete
    e19d3c1de3ca: Pull complete
    Digest: sha256:842a4a39a3c48f5e8c1e2368a7fe1bb6d4bfca7aa0f4d0d2f3ab31b1dbbc89e7
    Status: Downloaded newer image for jbangdev/jbang-action:latest
    root@b4bb09cfb08b:/#

    root@b4bb09cfb08b:/# jbang trust add https://github.com/tors42/
    [jbang] Adding [https://github.com/tors42/] to /jbang/.jbang/trusted-sources.json
    root@b4bb09cfb08b:/#

    root@b4bb09cfb08b:/# jbang membercount@tors42/jbang-chariot lichess-swiss
    [jbang] Downloading JDK 17. Be patient, this can take several minutes...
    [jbang] Installing JDK 17...
    [jbang] Default JDK set to 17
    [jbang] Resolving dependencies...
    [jbang] io.github.tors42:chariot:jar:0.0.32
    Done
    [jbang] Dependencies resolved
    [jbang] Building jar...
    Lichess Swiss has 212816 members!

    root@b4bb09cfb08b:/#

And again, now when everything has been downloaded already,

    root@b4bb09cfb08b:/# jbang membercount@tors42/jbang-chariot lichess-swiss
    Lichess Swiss has 212816 members!

    root@b4bb09cfb08b:/#

