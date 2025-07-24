import kotlinx.serialization.json.*

fun main() {
    val jsonObject = buildJsonObject {
        put("title", "\"Quoted Title\"")
    }
    
    val value = jsonObject["title"]
    println("Value: $value")
    println("Value type: ${value?.javaClass}")
    println("Is JsonPrimitive: ${value is JsonPrimitive}")
    
    if (value is JsonPrimitive) {
        println("contentOrNull: ${value.contentOrNull}")
        println("toString: ${value.toString()}")
    }
}