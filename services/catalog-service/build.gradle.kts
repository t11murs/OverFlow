description = "OrderFlow catalog-service"

dependencies {
    add("implementation", "org.springframework.boot:spring-boot-starter-cache")
    add("implementation", "org.springframework.boot:spring-boot-starter-data-redis")
    add("implementation", "io.jsonwebtoken:jjwt-api:0.13.0")
    add("runtimeOnly", "io.jsonwebtoken:jjwt-impl:0.13.0")
    add("runtimeOnly", "io.jsonwebtoken:jjwt-jackson:0.13.0")
}
