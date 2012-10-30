/**
 *                            License
 * THE WORK (AS DEFINED BELOW) IS PROVIDED UNDER THE TERMS OF THIS  
 * CREATIVE COMMONS PUBLIC LICENSE ("CCPL" OR "LICENSE"). 
 * THE WORK IS PROTECTED BY COPYRIGHT AND/OR OTHER APPLICABLE LAW.  
 * ANY USE OF THE WORK OTHER THAN AS AUTHORIZED UNDER THIS LICENSE OR  
 * COPYRIGHT LAW IS PROHIBITED.
 * 
 * BY EXERCISING ANY RIGHTS TO THE WORK PROVIDED HERE, YOU ACCEPT AND  
 * AGREE TO BE BOUND BY THE TERMS OF THIS LICENSE. TO THE EXTENT THIS LICENSE  
 * MAY BE CONSIDERED TO BE A CONTRACT, THE LICENSOR GRANTS YOU THE RIGHTS CONTAINED 
 * HERE IN CONSIDERATION OF YOUR ACCEPTANCE OF SUCH TERMS AND CONDITIONS.
 * 
 */
package l1j.server.server.model;

import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.utils.Random;

import l1j.server.Config; // [旅馆][血盟小屋][城堡内城][妖森大树下]回血回魔量外部设定
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_bonusstats;
import l1j.server.server.types.Point;
import static l1j.server.server.model.skill.L1SkillId.*;

public class HpRegeneration extends TimerTask {

	private static final Logger _log = Logger.getLogger(HpRegeneration.class
			.getName());

	private final L1PcInstance _pc;

	private int _regenMax = 0;

	private int _regenPoint = 0;

	private int _curPoint = 4;

	public HpRegeneration(L1PcInstance pc) {
		_pc = pc;

		updateLevel();
	}

	public void setState(int state) {
		if (_curPoint < state) {
			return;
		}

		_curPoint = state;
	}

	@Override
	public void run() {
		try {
			if (_pc.isDead()) {
				return;
			}

			_regenPoint += _curPoint;
			_curPoint = 4;

			synchronized (this) {
				if (_regenMax <= _regenPoint) {
					_regenPoint = 0;
					regenHp();
				}
			}
		} catch (Throwable e) {
			_log.log(Level.WARNING, e.getLocalizedMessage(), e);
		}
	}

	public void updateLevel() {
		final int lvlTable[] = new int[] { 30, 25, 20, 16, 14, 12, 11, 10, 9,
				3, 2 };

		int regenLvl = Math.min(10, _pc.getLevel());
		if (30 <= _pc.getLevel() && _pc.isKnight()) {
			regenLvl = 11;
		}

		synchronized (this) {
			_regenMax = lvlTable[regenLvl - 1] * 4;
		}
	}

	public void regenHp() {
		if (_pc.isDead()) {
			return;
		}

		int maxBonus = 1;

		// CONボーナス
		if (11 < _pc.getLevel() && 14 <= _pc.getCon()) {
			maxBonus = _pc.getCon() - 12;
			if (25 < _pc.getCon()) {
				maxBonus = 14;
			}
		}

		int equipHpr = _pc.getInventory().hpRegenPerTick();
		equipHpr += _pc.getHpr();
		int bonus = Random.nextInt(maxBonus) + 1;

		if (_pc.hasSkillEffect(NATURES_TOUCH)) {
			bonus += 15;
		}
		if (L1HouseLocation.isInHouse(_pc.getX(), _pc.getY(), _pc.getMapId())) {
			//bonus += 5;
			bonus += Config.Hpr_InHouse; // [血盟小屋]回血量
		}
		if (_pc.getMapId() == 16384 || _pc.getMapId() == 16896
				|| _pc.getMapId() == 17408 || _pc.getMapId() == 17920
				|| _pc.getMapId() == 18432 || _pc.getMapId() == 18944
				|| _pc.getMapId() == 19968 || _pc.getMapId() == 19456
				|| _pc.getMapId() == 20480 || _pc.getMapId() == 20992
				|| _pc.getMapId() == 21504 || _pc.getMapId() == 22016
				|| _pc.getMapId() == 22528 || _pc.getMapId() == 23040
				|| _pc.getMapId() == 23552 || _pc.getMapId() == 24064
				|| _pc.getMapId() == 24576 || _pc.getMapId() == 25088) { // 宿屋
			//bonus += 5;
			bonus += Config.Hpr_InHotel; // [旅馆]回血量
		}
		// [城堡内城]回血量
		if (_pc.getMapId() == 15 || _pc.getMapId() == 29
				|| _pc.getMapId() == 52 || _pc.getMapId() == 64
				|| _pc.getMapId() == 300) {
			bonus += Config.Hpr_InCastle;
		}
		// end
		if ((_pc.getLocation().isInScreen(new Point(33055,32336))
				&& _pc.getMapId() == 4 && _pc.isElf())) {
			//bonus += 5;
			bonus += Config.Hpr_UnderTheTree; // [妖森大树下]回血量
		}
 		if (_pc.hasSkillEffect(COOKING_1_5_N)
				|| _pc.hasSkillEffect(COOKING_1_5_S)) {
			bonus += 3;
		}
 		if (_pc.hasSkillEffect(COOKING_2_4_N)
				|| _pc.hasSkillEffect(COOKING_2_4_S)
				|| _pc.hasSkillEffect(COOKING_3_6_N)
				|| _pc.hasSkillEffect(COOKING_3_6_S)) {
			bonus += 2;
		}
 		if (_pc.getOriginalHpr() > 0) { // オリジナルCON HPR补正
 			bonus += _pc.getOriginalHpr();
 		}

		boolean inLifeStream = false;
		if (isPlayerInLifeStream(_pc)) {
			inLifeStream = true;
			// 古代の空间、魔族の神殿ではHPR+3はなくなる？
			bonus += 3;
		}

		// 空腹と重量のチェック
		if (_pc.get_food() < 3 || isOverWeight(_pc)
				|| _pc.hasSkillEffect(BERSERKERS)) {
			bonus = 0;
			// 装备によるＨＰＲ增加は满腹度、重量によってなくなるが、 减少である场合は满腹度、重量に关系なく效果が残る
			if (equipHpr > 0) {
				equipHpr = 0;
			}
		}
		// 免登出可以把能力值點完 chinaabc
		if (_pc.getLevel() >= 51 && _pc.getLevel() - 50 > _pc.getBonusStats()) {
			if ((_pc.getBaseStr() + _pc.getBaseDex() + _pc.getBaseCon()
					+ _pc.getBaseInt() + _pc.getBaseWis() + _pc.getBaseCha()) < (_pc
					.getLevel() - 50)+_pc.getElixirStats()+
					+ (_pc.getOriginalCha() + _pc.getOriginalCon()
							+ _pc.getOriginalDex() + _pc.getOriginalInt()
							+ _pc.getOriginalStr() + _pc.getOriginalWis())) {
				_pc.sendPackets(new S_bonusstats(_pc.getId(), 1));
			}
		}
		// END
		int newHp = _pc.getCurrentHp();
		newHp += bonus + equipHpr;

		if (newHp < 1) {
			newHp = 1; // ＨＰＲ减少装备によって死亡はしない
		}
		// 水中での减少处理
		// ライフストリームで减少をなくせるか不明
		if (isUnderwater(_pc)) {
			newHp -= 20;
			if (newHp < 1) {
				if (_pc.isGm()) {
					newHp = 1;
				} else {
					_pc.death(null); // 窒息によってＨＰが０になった场合は死亡する。
				}
			}
		}
		// Lv50クエストの古代の空间1F2Fでの减少处理
		if (isLv50Quest(_pc) && !inLifeStream) {
			newHp -= 10;
			if (newHp < 1) {
				if (_pc.isGm()) {
					newHp = 1;
				} else {
					_pc.death(null); // ＨＰが０になった场合は死亡する。
				}
			}
		}
		// 魔族の神殿での减少处理
		if (_pc.getMapId() == 410 && !inLifeStream) {
			newHp -= 10;
			if (newHp < 1) {
				if (_pc.isGm()) {
					newHp = 1;
				} else {
					_pc.death(null); // ＨＰが０になった场合は死亡する。
				}
			}
		}

		if (!_pc.isDead()) {
			_pc.setCurrentHp(Math.min(newHp, _pc.getMaxHp()));
		}
	}

	private boolean isUnderwater(L1PcInstance pc) {
		// ウォーターブーツ装备时か、 エヴァの祝福状态、修理された装备セットであれば水中では无いとみなす。
		if (pc.getInventory().checkEquipped(20207)) {
			return false;
		}
		if (pc.hasSkillEffect(STATUS_UNDERWATER_BREATH)) {
			return false;
		}
		if (pc.getInventory().checkEquipped(21048)
				&& pc.getInventory().checkEquipped(21049)
				&& pc.getInventory().checkEquipped(21050)) {
			return false;
		}

		return pc.getMap().isUnderwater();
	}

	private boolean isOverWeight(L1PcInstance pc) {
		// エキゾチックバイタライズ状态、アディショナルファイアー状态か
		// ゴールデンウィング装备时であれば、重量オーバーでは无いとみなす。
		if (pc.hasSkillEffect(EXOTIC_VITALIZE)
				|| pc.hasSkillEffect(ADDITIONAL_FIRE)) {
			return false;
		}
		if (pc.getInventory().checkEquipped(20049)) {
			return false;
		}

		return (121 <= pc.getInventory().getWeight242()) ? true : false;
	}

	private boolean isLv50Quest(L1PcInstance pc) {
		int mapId = pc.getMapId();
		return (mapId == 2000 || mapId == 2001) ? true : false;
	}

	/**
	 * 指定したPCがライフストリームの范围内にいるかチェックする
	 * 
	 * @param pc
	 *            PC
	 * @return true PCがライフストリームの范围内にいる场合
	 */
	private static boolean isPlayerInLifeStream(L1PcInstance pc) {
		for (L1Object object : pc.getKnownObjects()) {
			if (object instanceof L1EffectInstance == false) {
				continue;
			}
			L1EffectInstance effect = (L1EffectInstance) object;
			if (effect.getNpcId() == 81169 && effect.getLocation()
					.getTileLineDistance(pc.getLocation()) < 4) {
				return true;
			}
		}
		return false;
	}
}