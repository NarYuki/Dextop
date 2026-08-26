package moe.n4tsu.dextop.privilege

data class RuntimeCommandResult(
    val exitCode: Int,
    val output: String,
    val error: String,
)
