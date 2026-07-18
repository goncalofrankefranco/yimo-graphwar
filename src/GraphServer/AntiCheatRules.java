package GraphServer;

/** Small, protocol-level checks shared by the room server and its tests. */
public final class AntiCheatRules
{
	private AntiCheatRules()
	{
	}

	public static boolean isSafeFunction(String function)
	{
		if(function == null || function.trim().length() == 0 || function.length() > Constants.MAX_FUNCTION_LENGTH)
		{
			return false;
		}
		for(int i=0; i<function.length(); i++)
		{
			char character = function.charAt(i);
			if(character < 0x20 || character == 0x7f || character == '&')
			{
				return false;
			}
		}
		return true;
	}

	public static boolean isSafeAngle(double angle)
	{
		return !Double.isNaN(angle) && !Double.isInfinite(angle)
				&& angle >= -Math.PI/2.0 && angle <= Math.PI/2.0;
	}
}
