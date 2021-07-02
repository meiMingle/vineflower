package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchAllStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.CatchStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.struct.StructClass;

import java.util.ArrayList;

public class TryHelper {
  public static boolean enhanceTryStats(RootStatement root, StructClass cl) {
    boolean ret = makeTryWithResourceRec(cl, root);

    if (ret) {
      if (cl.isVersion(CodeConstants.BYTECODE_JAVA_11)) {
        SequenceHelper.condenseSequences(root);
      } else {
        SequenceHelper.condenseSequences(root);
        if (collapseTryRec(root)) {
          SequenceHelper.condenseSequences(root);
        }
      }
    }

    return ret;
  }

  private static boolean makeTryWithResourceRec(StructClass cl, Statement stat) {
    if (cl.isVersion(CodeConstants.BYTECODE_JAVA_11)) {
      if (stat.type == Statement.TYPE_TRYCATCH) {
        if (TryWithResourcesHelper.makeTryWithResourceJ11((CatchStatement) stat)) {
          return true;
        }
      }
    } else {
      if (stat.type == Statement.TYPE_CATCHALL && ((CatchAllStatement) stat).isFinally()) {
        if (TryWithResourcesHelper.makeTryWithResource((CatchAllStatement) stat)) {
          return true;
        }
      }
    }

    for (Statement st : new ArrayList<>(stat.getStats())) {
      if (makeTryWithResourceRec(cl, st)) {
        return true;
      }
    }

    return false;
  }

  private static boolean collapseTryRec(Statement stat) {
    if (stat.type == Statement.TYPE_TRYCATCH && collapseTry((CatchStatement)stat)) {
      return true;
    }

    for (Statement st : stat.getStats()) {
      if (collapseTryRec(st)) {
        return true;
      }
    }

    return false;
  }

  private static boolean collapseTry(CatchStatement catchStat) {
    Statement parent = catchStat;
    if (parent.getFirst() != null && parent.getFirst().type == Statement.TYPE_SEQUENCE) {
      parent = parent.getFirst();
    }
    if (parent != null && parent.getFirst() != null && parent.getFirst().type == Statement.TYPE_TRYCATCH) {
      CatchStatement toRemove = (CatchStatement)parent.getFirst();

      if (toRemove.getTryType() == CatchStatement.RESOURCES) {
        catchStat.setTryType(CatchStatement.RESOURCES);
        catchStat.getResources().addAll(toRemove.getResources());

        catchStat.getVarDefinitions().addAll(toRemove.getVarDefinitions());
        parent.replaceStatement(toRemove, toRemove.getFirst());

        if (!toRemove.getVars().isEmpty()) {
          for (int i = 0; i < toRemove.getVars().size(); ++i) {
            catchStat.getVars().add(i, toRemove.getVars().get(i));
            catchStat.getExctStrings().add(i, toRemove.getExctStrings().get(i));

            catchStat.getStats().add(i + 1, catchStat.getStats().get(i + 1));
          }
        }
        return true;
      }
    }
    return false;
  }
}
