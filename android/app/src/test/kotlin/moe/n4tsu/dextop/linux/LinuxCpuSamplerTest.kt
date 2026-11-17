package moe.n4tsu.dextop.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxCpuSamplerTest {
    private fun stat(pid: Int, comm: String, state: Char, user: Long, system: Long, start: Long, cpu: Int): String {
        val fields = MutableList(50) { "0" }
        fields[0] = state.toString(); fields[11] = user.toString(); fields[12] = system.toString()
        fields[19] = start.toString(); fields[36] = cpu.toString()
        return "$pid ($comm) " + fields.joinToString(" ")
    }

    @Test
    fun parsesCommandsWithSpacesAndParentheses() {
        val process = LinuxCpuSampler.parse("42", stat(42, "a (b) c", 'R', 7, 3, 900, 2))!!
        assertEquals(LinuxCpuSampler.Process("42:900", 'R', 7, 3, 2), process)
    }

    @Test
    fun chargesNewTimeToTheLastCpuAndKeepsCountersGrowing() {
        val sampler = LinuxCpuSampler(cpus = 2, proc = java.io.File("/nonexistent"))
        sampler.sample(10.0, listOf(LinuxCpuSampler.Process("1:5", 'S', 100, 50, 0)))
        sampler.sample(11.0, listOf(LinuxCpuSampler.Process("1:5", 'R', 140, 60, 1)))
        val lines = sampler.stat(0).lines()
        val cpu1 = lines[2].split(' ').drop(1).map(String::toLong)
        assertEquals(40L + 10L, cpu1[0] + cpu1[2])
        // 10 s idle before the first sample, then half of the second one.
        assertEquals(1050L, cpu1[3])
        // The process exited: its time stays counted.
        sampler.sample(12.0, emptyList())
        val after = sampler.stat(0).lines()[0].split(Regex(" +")).drop(1).map(String::toLong)
        assertEquals(200L, after[0] + after[2])
        assertTrue(sampler.loadavg().startsWith("0."))
    }
}
