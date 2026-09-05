package io.github.mahjongmaid.maid;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

@LittleMaidExtension
public final class MaidExtension implements ILittleMaid {
    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new MahjongTask());
    }
}
