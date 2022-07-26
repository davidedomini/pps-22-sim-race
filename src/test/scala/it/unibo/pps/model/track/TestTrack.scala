package it.unibo.pps.model.track

import it.unibo.pps.model.loader.TrackLoader
import it.unibo.pps.model.track.{Straight, Turn}
import it.unibo.pps.model.RenderStraightParams
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class TestTrack extends AnyFlatSpec with Matchers:

  "An empty track" should "return an empty List" in {
    val t = Track()
    t.sectors shouldBe List.empty
  }

  "After adding a sector the track" should "be non-empty" in {
    val t = Track()
    val sector = Straight(1, Direction.Forward, RenderStraightParams((0, 0), (0, 0), (0, 0), (0, 0), 0))
    t.addSector(sector)
    t.sectors.size should be > 0
  }

  "With track loader you" should "create a base track" in {
    val track = TrackLoader("/prolog/basetrack.pl").load
    track.sectors.size shouldBe 4
    track.sectors
      .filter(s =>
        s match {
          case straight: Straight => true
          case _ => false
        }
      )
      .size shouldBe 2
    track.sectors
      .filter(s =>
        s match {
          case turn: Turn => true
          case _ => false
        }
      )
      .size shouldBe 2
  }

  "Next sector" should "return the next sector" in {
    val track = TrackLoader("/prolog/basetrack.pl").load
    val sector = Straight(1, Direction.Forward, RenderStraightParams((0, 0), (0, 0), (0, 0), (0, 0), 0))
    val nextSector = track.nextSector(sector)
    nextSector.id shouldBe sector.id + 1
  }

  "Next sector" should "also work cyclically" in {
    val track = TrackLoader("/prolog/basetrack.pl").load
    val sector = Straight(4, Direction.Forward, RenderStraightParams((0, 0), (0, 0), (0, 0), (0, 0), 0))
    val nextSector = track.nextSector(sector)
    nextSector.id shouldBe 1
  }
