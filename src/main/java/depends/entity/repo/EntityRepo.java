/*
MIT License

Copyright (c) 2018-2019 Gang ZHANG

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package depends.entity.repo;

import java.util.Collection;
import java.util.Iterator;

import depends.entity.Entity;
import depends.entity.FileEntity;
import depends.entity.GenericName;

public interface EntityRepo extends IdGenerator {
	public static final String GLOBAL_SCOPE_NAME = "::GLOBAL::";

	Entity getEntity(String entityName);

	Entity getEntity(Integer entityId);

	Entity getEntity(GenericName rawName);

	void add(Entity entity);

	Iterator<Entity> entityIterator();

	void updateEntityPath(Entity entity, String newPath);

	Collection<Entity> getFileEntities();

	Collection<Entity> getAllEntities();

	Iterator<Entity> sortedFileIterator();

	void addFile(FileEntity currentFileEntity);

	void removeEntity(Entity entity);

	void putEntityByName(Entity entity, String name);
}
