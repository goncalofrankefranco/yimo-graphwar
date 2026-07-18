package GraphServer;

import java.util.ArrayList;
import java.util.List;

/** Regression checks for server-owned turn and shot state. */
public final class AuthoritativeGameTest
{
	private static void check(boolean condition, String message)
	{
		if(!condition)
		{
			throw new AssertionError(message);
		}
	}

	public static void main(String[] args)
	{
		List<Player> players = new ArrayList<Player>();
		Player first = new Player("first");
		Player second = new Player("second");
		first.setNumSoldiers(1);
		second.setNumSoldiers(1);
		players.add(first);
		players.add(second);

		AuthoritativeGame game = new AuthoritativeGame();
		game.start(new MapShape[0], new int[] {100, 100, 600, 400}, players, 0,
				Constants.NORMAL_FUNC, Constants.SHOOTER_RELATIVE_TRAJECTORY);

		check(game.getCurrentPlayerID() == first.getID(), "the server must own the starting turn");
		check(game.acceptShot(second.getID(), "0") == null, "a non-current player must not fire");
		check(game.acceptShot(first.getID(), "0") != null, "the current player must be able to fire once");
		check(game.acceptShot(first.getID(), "0") == null, "a player must not fire twice in one turn");
		check(game.hasShotInTurn(), "accepted shots must close the turn to additional shots");

		game.advanceTurn();
		check(game.getCurrentPlayerID() == second.getID(), "the server must advance to the next player");
	}
}
