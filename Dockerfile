# ── Stage 1: Compile Java ──────────────────────────────────
FROM eclipse-temurin:18-jdk AS builder
WORKDIR /build

COPY src/ src/
COPY WebContent/WEB-INF/lib/ lib/

# Jakarta Servlet API — compile-time only (Tomcat ships its own)
ADD https://repo1.maven.org/maven2/jakarta/servlet/jakarta.servlet-api/5.0.0/jakarta.servlet-api-5.0.0.jar lib/jakarta.servlet-api-5.0.0.jar

RUN mkdir -p classes && \
    find src -name '*.java' > sources.txt && \
    javac -cp "lib/*" -d classes -source 17 -target 17 @sources.txt

# ── Stage 2: Tomcat runtime ────────────────────────────────
FROM tomcat:10.0

RUN rm -rf /usr/local/tomcat/webapps/*
RUN mkdir -p /usr/local/tomcat/webapps/ROOT/WEB-INF/classes \
             /usr/local/tomcat/webapps/ROOT/WEB-INF/lib

COPY WebContent/                        /usr/local/tomcat/webapps/ROOT/
COPY --from=builder /build/classes/     /usr/local/tomcat/webapps/ROOT/WEB-INF/classes/
COPY WebContent/WEB-INF/lib/            /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/
COPY config/                            /usr/local/tomcat/config/

EXPOSE 8080
CMD ["catalina.sh", "run"]
