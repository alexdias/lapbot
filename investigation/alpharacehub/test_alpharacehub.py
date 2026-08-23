import unittest

from alpharacehub import TimingState


class TimingStateTest(unittest.TestCase):
    def test_sparse_lap_patches_are_merged(self):
        state = TimingState(
            {
                "Sequence": 10,
                "Competitors": [
                    {
                        "CompetitorId": 7,
                        "CompetitorNumber": "12",
                        "CompetitorName": "Test Driver",
                        "Laps": [{"LapNumber": 2, "Split1Time": 1000}],
                    }
                ],
            }
        )

        events = state.apply(
            {
                "Sequence": 11,
                "Competitors": [
                    {
                        "CompetitorId": 7,
                        "Laps": [
                            {"LapNumber": 2, "Split2Time": 2000, "LapTime": 6000}
                        ],
                    }
                ],
            }
        )

        self.assertEqual(state.snapshot["Sequence"], 11)
        self.assertEqual(state.competitors[0]["Laps"][0]["Split1Time"], 1000)
        self.assertEqual(events[0]["name"], "Test Driver")
        self.assertEqual(events[0]["sector_2_ms"], 2000)
        self.assertTrue(events[0]["complete"])


if __name__ == "__main__":
    unittest.main()
