package GraphServer;

import java.util.List;

import Graphwar.Function;
import Graphwar.MalformedFunction;
import Graphwar.Obstacle;

/**
 * Server-owned copy of the small amount of game state needed to validate a shot.
 * The clients still animate the returned trajectory, but they no longer choose
 * which soldiers were hit.
 */
public final class AuthoritativeGame
{
	public static final class Shot
	{
		private final int playerID;
		private final String function;
		private final double fireAngle;
		private final int[] hitPlayerIDs;
		private final int[] hitSoldierIndexes;
		private final int[] hitPositions;

		private Shot(int playerID, String function, double fireAngle,
				int[] hitPlayerIDs, int[] hitSoldierIndexes, int[] hitPositions)
		{
			this.playerID = playerID;
			this.function = function;
			this.fireAngle = fireAngle;
			this.hitPlayerIDs = hitPlayerIDs;
			this.hitSoldierIndexes = hitSoldierIndexes;
			this.hitPositions = hitPositions;
		}

		public int getPlayerID()
		{
			return playerID;
		}

		public String getFunction()
		{
			return function;
		}

		public double getFireAngle()
		{
			return fireAngle;
		}

		public int getHitCount()
		{
			return hitPlayerIDs.length;
		}

		public int getHitPlayerID(int index)
		{
			return hitPlayerIDs[index];
		}

		public int getHitSoldierIndex(int index)
		{
			return hitSoldierIndexes[index];
		}

		public int getHitPosition(int index)
		{
			return hitPositions[index];
		}
	}

	private Graphwar.Player[] players;
	private Obstacle obstacle;
	private int currentTurn;
	private int gameMode;
	private int trajectoryMode;
	private boolean shotInTurn;
	private boolean started;

	public void start(MapShape[] shapes, int[] soldierCoordinates, List<Player> definitions,
			int startPlayer, int gameMode, int trajectoryMode)
	{
		if(definitions == null || definitions.isEmpty() || shapes == null || soldierCoordinates == null)
		{
			throw new IllegalArgumentException("The server game needs players, a map, and soldier positions");
		}

		players = new Graphwar.Player[definitions.size()];
		int coordinate = 0;
		for(int i=0; i<definitions.size(); i++)
		{
			Player definition = definitions.get(i);
			players[i] = Graphwar.Player.createServerPlayer(definition.getName(), definition.getID(),
					definition.getTeam(), definition.getNumSoldiers());
			for(int soldier = 0; soldier < definition.getNumSoldiers(); soldier++)
			{
				if(coordinate + 1 >= soldierCoordinates.length)
				{
					throw new IllegalArgumentException("Missing soldier position");
				}
				players[i].startSoldier(soldier, soldierCoordinates[coordinate], soldierCoordinates[coordinate+1]);
				coordinate += 2;
			}
			players[i].restartTurn();
		}
		if(coordinate != soldierCoordinates.length)
		{
			throw new IllegalArgumentException("Unexpected soldier position");
		}

		obstacle = new Obstacle(shapes.length, shapes);
		currentTurn = Math.floorMod(startPlayer, players.length);
		this.gameMode = gameMode;
		this.trajectoryMode = trajectoryMode;
		shotInTurn = false;
		started = true;
	}

	public boolean isStarted()
	{
		return started;
	}

	public int getCurrentPlayerID()
	{
		return started ? players[currentTurn].getID() : -1;
	}

	public int getCurrentSoldierIndex()
	{
		return started ? players[currentTurn].getCurrentTurnSoldierIndex() : -1;
	}

	public boolean hasShotInTurn()
	{
		return shotInTurn;
	}

	public Shot acceptShot(int playerID, String functionString)
	{
		if(!started || shotInTurn || playerID != getCurrentPlayerID()
				|| !AntiCheatRules.isSafeFunction(functionString))
		{
			return null;
		}

		Graphwar.Player player = players[currentTurn];
		Function calculated;
		try
		{
			calculated = new Function(functionString);
			if(trajectoryMode == Constants.GLOBAL_TRAJECTORY)
			{
				calculated.processGlobalRange(obstacle, players, players.length, currentTurn, gameMode);
			}
			else if(player.getTeam() == Constants.TEAM1)
			{
				processRelative(calculated, false);
			}
			else
			{
				processRelative(calculated, true);
			}
		}
		catch(MalformedFunction e)
		{
			return null;
		}
		catch(RuntimeException e)
		{
			return null;
		}

		int hitCount = calculated.getNumPlayersHit();
		int[] hitPlayerIDs = new int[hitCount];
		int[] hitSoldierIndexes = new int[hitCount];
		int[] hitPositions = new int[hitCount];
		for(int i=0; i<hitCount; i++)
		{
			int hitPlayer = calculated.getPlayerHit(i);
			int hitSoldier = calculated.getSoldierHit(i);
			hitPlayerIDs[i] = players[hitPlayer].getID();
			hitSoldierIndexes[i] = hitSoldier;
			hitPositions[i] = calculated.getSoldierHitPosition(i);
			players[hitPlayer].getSoldiers()[hitSoldier].setAlive(false);
		}

		player.getCurrentTurnSoldier().setFunction(functionString);
		player.getCurrentTurnSoldier().setAngle(calculated.getFireAngle());
		int explosionX = (int) calculated.getLastX();
		if(trajectoryMode != Constants.GLOBAL_TRAJECTORY && player.getTeam() == Constants.TEAM2)
		{
			explosionX = Constants.PLANE_LENGTH - explosionX;
		}
		obstacle.setExplosion(explosionX, (int) calculated.getLastY(), Constants.EXPLOSION_RADIUS);
		obstacle.explodePoint();
		shotInTurn = true;

		return new Shot(playerID, functionString, calculated.getFireAngle(),
				hitPlayerIDs, hitSoldierIndexes, hitPositions);
	}

	private void processRelative(Function calculated, boolean inverted)
	{
		switch(gameMode)
		{
			case Constants.NORMAL_FUNC:
				calculated.processFunctionRange(obstacle, players, players.length, currentTurn, inverted);
				break;
			case Constants.FST_ODE:
				calculated.processRK4Range(obstacle, players, players.length, currentTurn, inverted);
				break;
			case Constants.SND_ODE:
				calculated.processRK42Range(obstacle, players, players.length, currentTurn,
						players[currentTurn].getCurrentTurnSoldier().getAngle(), inverted);
				break;
			default:
				throw new IllegalArgumentException("Unknown game mode");
		}
	}

	public boolean setAngle(int playerID, int soldierIndex, double angle)
	{
		if(!started || shotInTurn || playerID != getCurrentPlayerID()
				|| soldierIndex != getCurrentSoldierIndex() || !AntiCheatRules.isSafeAngle(angle))
		{
			return false;
		}
		players[currentTurn].getCurrentTurnSoldier().setAngle(angle);
		return true;
	}

	public void advanceTurn()
	{
		if(!started)
		{
			return;
		}
		for(int i=0; i<players.length; i++)
		{
			currentTurn = (currentTurn + 1) % players.length;
			if(players[currentTurn].nextTurn())
			{
				break;
			}
		}
		shotInTurn = false;
	}

	public boolean isGameFinished()
	{
		if(!started)
		{
			return false;
		}
		boolean team1Alive = false;
		boolean team2Alive = false;
		for(Graphwar.Player player : players)
		{
			for(int i=0; i<player.getNumSoldiers(); i++)
			{
				if(player.getSoldiers()[i].isAlive())
				{
					if(player.getTeam() == Constants.TEAM1)
					{
						team1Alive = true;
					}
					else
					{
						team2Alive = true;
					}
				}
			}
		}
		return !team1Alive || !team2Alive;
	}
}
