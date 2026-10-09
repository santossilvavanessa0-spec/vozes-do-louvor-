package com.example

import com.example.audio.MusicalKeyDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCifraClubKeyDetection_bondadeDeDeus() = runBlocking {
    val tempFile = File.createTempFile("bondade_de_deus", ".mp3")
    try {
      val result = MusicalKeyDetector.detectKey(tempFile, "Bondade de Deus - Isaias Saad.mp3")
      assertEquals("G", result.rootKey)
      assertEquals("Sol Maior (G)", result.fullKeyName)
      assertTrue(result.detectionSource.contains("Cifra Club"))
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun testCifraClubKeyDetection_aBencao() = runBlocking {
    val tempFile = File.createTempFile("a_bencao", ".wav")
    try {
      val result = MusicalKeyDetector.detectKey(tempFile, "A Bênção - Gabriel Guedes.wav")
      assertEquals("B", result.rootKey)
      assertEquals("Si Maior (B)", result.fullKeyName)
      assertTrue(result.detectionSource.contains("Cifra Club"))
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun testCifraClubKeyDetection_porqueEleVive() = runBlocking {
    val tempFile = File.createTempFile("porque_ele_vive", ".m4a")
    try {
      val result = MusicalKeyDetector.detectKey(tempFile, "Porque Ele Vive - Harpa Crista.m4a")
      assertEquals("A", result.rootKey)
      assertEquals("Lá Maior (A)", result.fullKeyName)
      assertTrue(result.detectionSource.contains("Cifra Club"))
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun testCifraClubKeyDetection_lugarSecreto() = runBlocking {
    val tempFile = File.createTempFile("lugar_secreto", ".mp3")
    try {
      val result = MusicalKeyDetector.detectKey(tempFile, "Lugar Secreto - Gabriela Rocha.mp3")
      assertEquals("G", result.rootKey)
      assertTrue(result.detectionSource.contains("Cifra Club"))
    } finally {
      tempFile.delete()
    }
  }
}
