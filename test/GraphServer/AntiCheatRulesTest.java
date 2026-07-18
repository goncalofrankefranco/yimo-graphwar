package GraphServer;

/** Regression checks for untrusted protocol values. */
public final class AntiCheatRulesTest
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
		check(AntiCheatRules.isSafeFunction("sin(x)+2"), "ordinary functions must remain valid");
		check(!AntiCheatRules.isSafeFunction(null), "null functions must be rejected");
		check(!AntiCheatRules.isSafeFunction("x\n0"), "line breaks must not cross the protocol boundary");
		check(!AntiCheatRules.isSafeFunction("x&0"), "message delimiters must not be accepted in a function");
		check(!AntiCheatRules.isSafeFunction(repeat('x', Constants.MAX_FUNCTION_LENGTH+1)),
				"oversized functions must be rejected");
		check(AntiCheatRules.isSafeAngle(0), "zero is a valid angle");
		check(!AntiCheatRules.isSafeAngle(Double.NaN), "NaN angles must be rejected");
		check(!AntiCheatRules.isSafeAngle(Double.POSITIVE_INFINITY), "infinite angles must be rejected");
		check(!AntiCheatRules.isSafeAngle(Math.PI), "angles outside the firing range must be rejected");
	}

	private static String repeat(char character, int length)
	{
		StringBuilder result = new StringBuilder(length);
		for(int i=0; i<length; i++)
		{
			result.append(character);
		}
		return result.toString();
	}
}
