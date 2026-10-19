package Graphwar;

import GraphServer.Constants;

/** Derives brief shot feedback only from the room server's hit result. */
final class ShotFeedback {
    private ShotFeedback() {
    }

    static String textFor(int shooterTeam, int[] hitTeams) {
        if (hitTeams == null) {
            return null;
        }
        int opponentTeam;
        if (shooterTeam == Constants.TEAM1) {
            opponentTeam = Constants.TEAM2;
        } else if (shooterTeam == Constants.TEAM2) {
            opponentTeam = Constants.TEAM1;
        } else {
            return null;
        }
        for (int hitTeam : hitTeams) {
            if (hitTeam == opponentTeam) {
                return "DIRECT HIT!";
            }
        }
        return null;
    }
}
